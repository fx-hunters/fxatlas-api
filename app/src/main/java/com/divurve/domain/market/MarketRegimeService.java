package com.divurve.domain.market;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.forecast.PairCode;
import com.divurve.domain.port.FxRateHistoryProvider;
import com.divurve.engine.forecast.VolatilityCalculator;
import com.divurve.engine.volatility.MarketChecks;
import com.divurve.engine.volatility.Regime;
import com.divurve.engine.volatility.RegimeBadgeMapper;
import com.divurve.engine.volatility.RegimeClassifier;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장 상태 조회 UseCase ({@code GET /market/regime}, 명세 v2 §5.10, FR-SF-01~06).
 *
 * <h2>v1 안전모드를 대체한다 — 그러나 아무것도 끄지 않는다</h2>
 * v1 은 {@code GET /system/safe-mode} 가 6조건을 평가해 {@code 503 SAFE_MODE_ACTIVE} 로 응답을
 * <b>차단</b>했다. 명세 v2 §0.1 이 그 503 을 삭제했고 FR-SF-01 이 "급변 상태에서도 최신 정보를 숨기지
 * 않는다"로 바뀌었다. 그래서 이 서비스의 출력에는 차단에 해당하는 값이 없다 —
 * {@code keepServingForecast} 는 <b>상수 {@code true}</b> 이며 분기가 존재하지 않는다.
 *
 * <h2>매핑 책임은 서버</h2>
 * 국면 4종({@code calm/normal/elevated/stress}) → 배지 3종({@code normal/caution/turbulent}) 변환은
 * {@link RegimeBadgeMapper} 가 한다(명세 §2). 클라이언트는 {@code badge} 를 그대로 그린다.
 * 여러 통화쌍의 대표 배지는 <b>가장 심각한 국면</b> 기준이다 — 하나라도 급변이면 급변으로 알린다.
 */
@UseCase
public class MarketRegimeService {

    /**
     * 상태를 판정할 통화쌍 (명세 §4 "저장 통화쌍").
     *
     * <p>⚠️ 현재 외부 데이터 어댑터(ECOS)는 원화 크로스만 제공하므로 {@code USDJPY}·{@code EURUSD} 는
     * 조회가 실패한다. 실패한 통화쌍은 {@code pair_regimes} 에서 <b>빠진다</b> — 없는 근거를 만들지
     * 않는다(FR-CM-10). 삼각 유도로 채우는 것은 별도 과제다.
     */
    public static final List<String> PAIR_CODES = List.of("USDKRW", "USDJPY", "EURUSD");

    /** 5년 백분위 계산에 필요한 최소 관측 구간(영업일) + 30일 롤링 윈도. */
    private static final int HISTORY_WINDOW_DAYS = 5 * 252 + 30;

    /** 명세 §5.10 {@code anomaly.note}. 데이터 오류와 실제 시장 충격을 구분한다(FR-SF-06). */
    public static final String ANOMALY_NOTE =
            "데이터 오류와 실제 시장 충격은 구분하며 실제 충격은 삭제하지 않습니다.";

    private final FxRateHistoryProvider historyProvider;
    private final RegimeClassifier regimeClassifier;
    private final RegimeBadgeMapper badgeMapper;
    private final MarketChecks marketChecks;
    private final Clock clock;

    public MarketRegimeService(
            FxRateHistoryProvider historyProvider,
            RegimeClassifier regimeClassifier,
            RegimeBadgeMapper badgeMapper,
            MarketChecks marketChecks,
            Clock clock) {
        this.historyProvider = Objects.requireNonNull(historyProvider, "historyProvider");
        this.regimeClassifier = Objects.requireNonNull(regimeClassifier, "regimeClassifier");
        this.badgeMapper = Objects.requireNonNull(badgeMapper, "badgeMapper");
        this.marketChecks = Objects.requireNonNull(marketChecks, "marketChecks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 통화쌍별 국면과 대표 배지, 판정 근거, 클라이언트 안내를 만든다.
     *
     * @return 시장 상태
     */
    @Transactional(readOnly = true)
    public MarketRegimeView getRegime() {
        LocalDate asOfDate = LocalDate.now(clock);

        Map<String, PairRegimeView> pairRegimes = new LinkedHashMap<>();
        List<Regime> regimes = new ArrayList<>();
        LocalDate latestObservation = null;
        for (String rawPairCode : PAIR_CODES) {
            PairCode pair = PairCode.parse(rawPairCode);
            List<FxRateHistoryProvider.HistoryRateSnapshot> history = fetchHistoryOrEmpty(pair);
            if (history.size() < 2) {
                continue;
            }
            List<Double> dailyReturns = toDailyReturns(history);
            double vol30d;
            double volPercentile5y;
            try {
                vol30d = VolatilityCalculator.calculateRealized30d(dailyReturns);
                volPercentile5y = VolatilityCalculator.calculatePercentile5y(dailyReturns);
            } catch (IllegalArgumentException e) {
                // 관측이 모자란 통화쌍은 국면을 만들어내지 않는다 (FR-CM-10).
                continue;
            }
            Regime regime = regimeClassifier.classify(volPercentile5y);
            regimes.add(regime);
            pairRegimes.put(pair.canonical(), new PairRegimeView(regime.code(), vol30d, volPercentile5y));

            LocalDate lastDate = history.get(history.size() - 1).date();
            if (latestObservation == null || lastDate.isAfter(latestObservation)) {
                latestObservation = lastDate;
            }
        }

        Regime representative = representativeRegime(regimes);
        RegimeBadgeMapper.Badge badge = badgeMapper.toBadge(representative);

        MarketChecks.Check freshness = marketChecks.dataFreshness(latestObservation, asOfDate);
        // 비교 출처가 아직 하나뿐이라 괴리 판정은 대상이 없다 — 없는 근거를 만들지 않는다(FR-CM-10).
        MarketChecks.Check divergence = marketChecks.sourceDivergence(null, null);
        List<CheckView> checks = List.of(
                CheckView.from(freshness),
                CheckView.from(divergence),
                CheckView.from(worstVolPercentileCheck(pairRegimes)));

        boolean elevatedOrWorse = badge != RegimeBadgeMapper.Badge.NORMAL;

        return new MarketRegimeView(
                badge.code(),
                badge.label(),
                representative.code(),
                pairRegimes,
                checks,
                new GuidanceView(
                        // FR-SF-01 — 상수다. 산출을 멈추는 경로를 두지 않는다.
                        true,
                        elevatedOrWorse,
                        elevatedOrWorse),
                new AnomalyView(hasDataError(checks), ANOMALY_NOTE));
    }

    /**
     * 데이터 품질 문제 여부 (FR-SF-06).
     *
     * <p><b>실제 시장 충격은 데이터 오류가 아니다.</b> {@code vol_percentile} 실패는 변동성이 실제로
     * 커졌다는 뜻이므로 여기서 제외한다 — 그것을 오류로 취급하면 "실제 충격을 지우는" 경로가 생긴다.
     */
    private static boolean hasDataError(List<CheckView> checks) {
        return checks.stream()
                .anyMatch(check -> !check.passed()
                        && !MarketChecks.KEY_VOL_PERCENTILE.equals(check.key()));
    }

    /**
     * 대표 국면. 통화쌍이 하나도 없으면 {@link Regime#NORMAL} 로 둔다 — 데이터가 없다는 사실은
     * {@code checks.data_freshness} 가 알리고, 배지는 없는 급변을 주장하지 않는다.
     */
    private Regime representativeRegime(List<Regime> regimes) {
        if (regimes.isEmpty()) {
            return Regime.NORMAL;
        }
        return badgeMapper.worstOf(regimes);
    }

    /** 백분위가 가장 높은 통화쌍으로 {@code vol_percentile} 판정을 만든다. */
    private MarketChecks.Check worstVolPercentileCheck(Map<String, PairRegimeView> pairRegimes) {
        String worstPair = null;
        double worstPercentile = 0.0;
        for (Map.Entry<String, PairRegimeView> entry : pairRegimes.entrySet()) {
            if (worstPair == null || entry.getValue().volPercentile5y() > worstPercentile) {
                worstPair = entry.getKey();
                worstPercentile = entry.getValue().volPercentile5y();
            }
        }
        if (worstPair == null) {
            return new MarketChecks.Check(
                    MarketChecks.KEY_VOL_PERCENTILE, false, "변동성 백분위를 계산할 관측이 없습니다.");
        }
        return marketChecks.volPercentile(worstPair, worstPercentile);
    }

    private List<FxRateHistoryProvider.HistoryRateSnapshot> fetchHistoryOrEmpty(PairCode pair) {
        try {
            return historyProvider.fetchHistorical(
                    pair.providerCode(), LocalDate.now(clock), HISTORY_WINDOW_DAYS);
        } catch (RuntimeException e) {
            // 어댑터가 지원하지 않는 통화쌍. 판정에서 빠질 뿐 다른 통화쌍의 응답을 막지 않는다(FR-SF-01).
            return List.of();
        }
    }

    private static List<Double> toDailyReturns(List<FxRateHistoryProvider.HistoryRateSnapshot> history) {
        List<Double> returns = new ArrayList<>(history.size() - 1);
        for (int i = 1; i < history.size(); i++) {
            returns.add(Math.log(history.get(i).rate() / history.get(i - 1).rate()));
        }
        return returns;
    }

    /**
     * 시장 상태 (명세 §5.10 응답 전체).
     *
     * @param badge        배지 코드 {@code normal/caution/turbulent}
     * @param badgeLabel   배지 표시 문구
     * @param regime       대표 국면 {@code calm/normal/elevated/stress} (meta.regime 에도 실린다)
     * @param pairRegimes  통화쌍별 국면
     * @param checks       판정 근거. 실패해도 아무 기능을 끄지 않는다
     * @param guidance     클라이언트 표시 안내
     * @param anomaly      데이터 오류 여부 (실제 시장 충격과 구분)
     */
    public record MarketRegimeView(
            String badge,
            String badgeLabel,
            String regime,
            Map<String, PairRegimeView> pairRegimes,
            List<CheckView> checks,
            GuidanceView guidance,
            AnomalyView anomaly
    ) {
    }

    /**
     * 통화쌍 하나의 국면.
     *
     * @param regime          국면 코드
     * @param vol30d          30일 실현변동성 (연환산)
     * @param volPercentile5y 5년 변동성 백분위 (0~1 비율)
     */
    public record PairRegimeView(String regime, double vol30d, double volPercentile5y) {
    }

    /**
     * 판정 근거 한 항목 (명세 §5.10 {@code checks[]}).
     *
     * <p>engine 의 {@code MarketChecks.Check} 를 domain 어휘로 옮긴 것이다 — api 레이어가 engine 을
     * 직접 참조하지 못하게 하는 패키지 경계 규칙(CLAUDE.md 4장) 때문이다.
     *
     * @param key    항목 키 {@code data_freshness/source_divergence/vol_percentile}
     * @param passed 통과 여부. {@code false} 여도 어떤 기능도 끄지 않는다 (FR-SF-01)
     * @param detail 실패 사유. 통과 시 null
     */
    public record CheckView(String key, boolean passed, String detail) {

        static CheckView from(MarketChecks.Check check) {
            return new CheckView(check.key(), check.passed(), check.detail());
        }
    }

    /**
     * 클라이언트 표시 안내.
     *
     * @param keepServingForecast 항상 {@code true} (FR-SF-01)
     * @param widenUncertainty    불확실성 안내를 강화해 표시 (FR-SF-03)
     * @param showPlanAssumptions 기존 계획의 기준일·가정 확인 경로 노출 (FR-SF-04)
     */
    public record GuidanceView(
            boolean keepServingForecast,
            boolean widenUncertainty,
            boolean showPlanAssumptions
    ) {
    }

    /**
     * 이상 징후 (FR-SF-06).
     *
     * @param dataErrorDetected 데이터 품질 문제 여부. 실제 시장 충격(변동성 확대)은 여기 해당하지 않는다
     * @param note              구분 원칙 안내 문구
     */
    public record AnomalyView(boolean dataErrorDetected, String note) {
    }
}
