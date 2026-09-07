package com.divurve.domain.route;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.market.MarketRegimeService;
import com.divurve.domain.plan.PlanRateContext;
import com.divurve.domain.plan.PlanRateContextProvider;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.settings.RiskProfileView;
import com.divurve.domain.stress.StressRunService;
import com.divurve.domain.xray.XrayService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * RouteContext 직렬화 서비스 (API 명세 v2 §6.1, FR-RT-01).
 *
 * <p><b>계산은 하지 않는다.</b> 진단 · 자산 · 기준 환율 · 스트레스 결과를 모아 전달하는 계약만
 * 담당한다 — 계획 계산은 {@code PlanCalculationService} 의 몫이다.
 *
 * <p>블록 하나가 비어도 전체를 실패시키지 않는다. 이 응답은 여러 서비스를 <b>모아 보여주는</b>
 * 계약이므로, 스트레스 이력이 없다는 이유로 자산 요약까지 못 보게 만들 이유가 없다
 * (명세 §20 "데이터가 없으면 빈 상태 전용 처리").
 *
 * <p>🔒 {@code model_path} · {@code forecast_factors} 는 계약에서 제외되어 있다 (FR-FC-12).
 * 자세한 근거는 {@link RouteContext} 클래스 주석 참고.
 */
@UseCase
public class RouteContextService {

    private static final Logger log = LoggerFactory.getLogger(RouteContextService.class);

    /** RouteContext 의 기준 환율 블록에 쓰는 대표 통화. */
    private static final String DEFAULT_CURRENCY_CODE = "USD";

    private final Clock clock;
    private final XrayService xrayService;
    private final RiskProfileService riskProfileService;
    private final PlanRateContextProvider planRateContextProvider;
    private final StressRunService stressRunService;
    private final MarketRegimeService marketRegimeService;

    public RouteContextService(
            Clock clock,
            XrayService xrayService,
            RiskProfileService riskProfileService,
            PlanRateContextProvider planRateContextProvider,
            StressRunService stressRunService,
            MarketRegimeService marketRegimeService) {
        this.clock = requireNonNull(clock, "clock");
        this.xrayService = requireNonNull(xrayService, "xrayService");
        this.riskProfileService = requireNonNull(riskProfileService, "riskProfileService");
        this.planRateContextProvider =
                requireNonNull(planRateContextProvider, "planRateContextProvider");
        this.stressRunService = requireNonNull(stressRunService, "stressRunService");
        this.marketRegimeService = requireNonNull(marketRegimeService, "marketRegimeService");
    }

    /**
     * 사용자의 RouteContext 를 만든다.
     *
     * @param userId 조회 사용자
     * @return 진단·자산·기준 환율·스트레스 요약
     */
    @Transactional(readOnly = true)
    public RouteContext getContext(UUID userId) {
        requireNonNull(userId, "userId");
        return new RouteContext(
                Instant.now(clock),
                diagnosis(userId),
                portfolio(userId),
                forecast(userId),
                stress(userId),
                regime());
    }

    private RouteContext.Diagnosis diagnosis(UUID userId) {
        return read("diagnosis", () -> {
            RiskProfileView profile = riskProfileService.getRiskProfile(userId);
            return new RouteContext.Diagnosis(
                    profile.status(),
                    profile.riskType(),
                    profile.score(),
                    profile.concentrationThreshold());
        }).orElseGet(RouteContext.Diagnosis::empty);
    }

    private RouteContext.Portfolio portfolio(UUID userId) {
        return read("portfolio", () -> {
            XrayService.PortfolioSnapshot snapshot = xrayService.getPortfolio(userId);
            return new RouteContext.Portfolio(
                    snapshot.totalAssetKrw(),
                    snapshot.fxAssetKrw(),
                    snapshot.fxRatio(),
                    snapshot.exposure());
        }).orElseGet(RouteContext.Portfolio::empty);
    }

    /**
     * 기준 환율 요약. 계획 계산이 쓰는 것과 <b>같은 전제</b>를 낸다 — 화면이 보는 환율과 계획이
     * 쓴 환율이 다르면 사용자가 수치를 대조할 수 없다.
     */
    private RouteContext.Forecast forecast(UUID userId) {
        return read("forecast", () -> {
            PlanRateContext rates =
                    planRateContextProvider.resolve(userId, DEFAULT_CURRENCY_CODE);
            return new RouteContext.Forecast(
                    DEFAULT_CURRENCY_CODE + "KRW",
                    rates.baseRate(),
                    new RouteContext.Forecast.Interval(rates.lowRate(), rates.highRate()),
                    null,
                    rates.rateAsOf().atZone(java.time.ZoneOffset.UTC).toLocalDate());
        }).orElseGet(RouteContext.Forecast::empty);
    }

    private RouteContext.Stress stress(UUID userId) {
        return read("stress", () -> {
            List<StressRunService.RunHistoryView> runs = stressRunService.listRuns(userId);
            if (runs.isEmpty()) {
                return RouteContext.Stress.empty();
            }
            StressRunService.RunHistoryView latest = runs.get(0);
            return new RouteContext.Stress(latest.id().toString(), latest.totalEffectKrw());
        }).orElseGet(RouteContext.Stress::empty);
    }

    private String regime() {
        return read("regime", () -> marketRegimeService.getRegime().regime()).orElse(null);
    }

    /**
     * 블록 하나를 읽되 실패는 삼킨다.
     *
     * <p>값을 <b>지어내지 않고</b> 비운다 — 명세 §20 은 데이터가 없을 때 MOCK 으로 조용히
     * 대체하는 것을 금지한다. 원인은 로그에 남긴다.
     */
    private <T> Optional<T> read(String block, java.util.function.Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (RuntimeException e) {
            log.warn("RouteContext 블록을 채우지 못해 비워 둡니다: {}", block, e);
            return Optional.empty();
        }
    }
}
