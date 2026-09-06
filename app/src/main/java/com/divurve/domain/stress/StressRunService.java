package com.divurve.domain.stress;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.FxAssetValuator;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.stress.entity.StressScenario;
import com.divurve.domain.stress.entity.StressTestRun;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.stress.StressCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스트레스 시나리오 실행·이력 조회 UseCase
 * ({@code POST /stress/runs} · {@code GET /stress/runs}, 명세 v2 §5.9, 요구사항 §4.8).
 *
 * <h2>이 서비스는 예측하지 않는다</h2>
 * 시나리오 충격률은 마스터에 저장된 <b>가정값</b>이고, 결과는 그 가정에 대한 조건부 산술이다(FR-ST-04).
 * 그래서 응답에 확률도, 행동 제안도 넣지 않는다 — 충격값 · 기준일 · 가정 설명만 공개한다.
 *
 * <h2>v1 대비 달라진 점</h2>
 * v1 {@code POST /xray/stress} 는 통화별 환율 충격만 계산하고 <b>아무것도 저장하지 않았다</b>.
 * v2 는 시나리오 마스터를 두고 실행 결과를 {@code stress_test_runs} 에 남긴다 —
 * 사용자에게 노출된 계산 근거이기 때문이다(FR-ST-05, ERD §12).
 */
@UseCase
public class StressRunService {

    /** 명세 §5.9 {@code conditional_note}. 결과가 예측이 아니라는 사실을 응답 자체에 싣는다(FR-ST-04). */
    public static final String CONDITIONAL_NOTE =
            "이 결과는 미래 예측이 아니라 입력한 충격값에 대한 조건부 계산입니다.";

    private final StressScenarioRepository scenarioRepository;
    private final StressTestRunRepository runRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final FxAssetValuator fxAssetValuator;
    private final StressCalculator stressCalculator;
    private final Clock clock;

    public StressRunService(
            StressScenarioRepository scenarioRepository,
            StressTestRunRepository runRepository,
            UserRepository userRepository,
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            FxAssetValuator fxAssetValuator,
            StressCalculator stressCalculator,
            Clock clock) {
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository, "scenarioRepository");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.holdingRepository = Objects.requireNonNull(holdingRepository, "holdingRepository");
        this.depositRepository = Objects.requireNonNull(depositRepository, "depositRepository");
        this.fxAssetValuator = Objects.requireNonNull(fxAssetValuator, "fxAssetValuator");
        this.stressCalculator = Objects.requireNonNull(stressCalculator, "stressCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 시나리오를 적용하고 결과를 저장한다.
     *
     * @param userId       실행 사용자 (NFR-SE-02 소유자 격리)
     * @param scenarioCode 적용할 시나리오 코드
     * @return 저장된 실행 결과
     * @throws InvalidRequestException {@code scenarioCode} 가 비어 있거나 계산 입력이 부적절한 경우
     * @throws NotFoundException       사용자나 시나리오가 없는 경우
     */
    @Transactional
    public RunView run(UUID userId, String scenarioCode) {
        Objects.requireNonNull(userId, "userId");
        if (scenarioCode == null || scenarioCode.isBlank()) {
            throw new InvalidRequestException("scenario_code 는 필수입니다.", "scenario_code");
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        StressScenario scenario = scenarioRepository.findById(scenarioCode)
                .orElseThrow(() -> new NotFoundException("시나리오를 찾을 수 없습니다: " + scenarioCode));

        Assets assets = valuateAssets(userId);
        double equityShockPct = scenario.getEquityShockPct().doubleValue();
        double fxShockPct = scenario.getFxShockPct().doubleValue();

        StressCalculator.ScenarioResult result;
        try {
            result = stressCalculator.applyScenario(
                    assets.equityAssetKrw(), assets.fxAssetKrw(), equityShockPct, fxShockPct);
        } catch (IllegalArgumentException e) {
            // engine 의 계약 위반은 사용자 입력·자산 상태 문제이므로 400 으로 표면화한다.
            throw new InvalidRequestException(e.getMessage(), "scenario_code");
        }

        LocalDate baseDate = LocalDate.now(clock);
        StressTestRun saved = runRepository.save(StressTestRun.create(
                owner,
                scenario.getScenarioCode(),
                baseDate,
                equityShockPct,
                fxShockPct,
                result.equityEffectKrw(),
                result.fxEffectKrw(),
                result.totalEffectKrw()));

        return new RunView(
                saved.getId(),
                toScenarioSummary(scenario),
                baseDate,
                equityShockPct,
                fxShockPct,
                result.equityAssetKrw(),
                result.fxAssetBeforeKrw(),
                result.equityEffectKrw(),
                result.fxEffectKrw(),
                result.totalEffectKrw(),
                result.fxAssetAfterKrw(),
                result.interpretationCode());
    }

    /**
     * 실행 이력을 최신순으로 조회한다. 이력은 삭제하지 않으므로 과거 결과가 그대로 남아 있다(FR-ST-05).
     *
     * <p>충격률은 실행 시점 스냅샷을 그대로 쓴다 — 시나리오 마스터가 이후 바뀌어도 과거 수치가 변하지 않는다.
     * 적용 전/후 금액은 저장돼 있지 않으므로 이력에서는 효과 3항만 내려보낸다(없는 값을 만들지 않는다, FR-CM-10).
     *
     * @param userId 조회 사용자
     * @return 실행 이력 (없으면 빈 목록)
     */
    @Transactional(readOnly = true)
    public List<RunHistoryView> listRuns(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        Map<String, StressScenario> scenarios = new HashMap<>();
        for (StressScenario scenario : scenarioRepository.findAll()) {
            scenarios.put(scenario.getScenarioCode(), scenario);
        }

        return runRepository.findByOwner_IdOrderByCreatedAtDesc(userId).stream()
                .map(run -> new RunHistoryView(
                        run.getId(),
                        toScenarioSummary(scenarios.get(run.getScenarioCode())),
                        run.getBaseDate(),
                        run.getEquityShockPct().doubleValue(),
                        run.getFxShockPct().doubleValue(),
                        run.getEquityEffectKrw().longValue(),
                        run.getFxEffectKrw().longValue(),
                        run.getTotalEffectKrw().longValue(),
                        run.getCreatedAt()))
                .toList();
    }

    /**
     * 해외주식 평가액과 외화자산 전체 평가액을 원화로 환산한다.
     *
     * <p>해외주식 = {@code holdings}, 외화자산 = {@code holdings + deposits} 다.
     * 원화 자산({@code krw_assets})은 스트레스 충격 대상이 아니므로 여기에 들어가지 않는다.
     * 고시 단위가 100단위인 통화(JPY)는 {@link QuoteUnitNormalizer} 로 1단위 환율로 정규화한다.
     */
    private Assets valuateAssets(UUID userId) {
        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);
        List<Deposit> deposits = depositRepository.findByOwner_Id(userId);

        // 환율을 못 구한 통화는 빠진다 — /xray 와 같은 규약이다(이슈 #57).
        // 예전에는 이 서비스만 예외를 그대로 올려, GBP 보유 사용자는 스트레스 실행이 400 이었다.
        Map<String, BigDecimal> perUnitRates =
                fxAssetValuator.fetchPerUnitRates(holdings, deposits);

        long equityAssetKrw = 0L;
        for (Holding holding : holdings) {
            BigDecimal rate = perUnitRates.get(holding.getCurrencyCode());
            if (rate == null) {
                continue;
            }
            equityAssetKrw += BigDecimal.valueOf(holding.getQuantity() * holding.getAvgPrice())
                    .multiply(rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }

        long depositKrw = 0L;
        for (Deposit deposit : deposits) {
            BigDecimal rate = perUnitRates.get(deposit.getCurrencyCode());
            if (rate == null) {
                continue;
            }
            depositKrw += deposit.getAmount()
                    .multiply(rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }

        return new Assets(equityAssetKrw, equityAssetKrw + depositKrw);
    }

    private static ScenarioSummary toScenarioSummary(StressScenario scenario) {
        if (scenario == null) {
            // 마스터에서 사라진 시나리오의 과거 이력. 이력은 지우지 않으므로 이름 없이 코드만 남는다.
            return null;
        }
        return new ScenarioSummary(
                scenario.getScenarioCode(),
                scenario.getNameKo(),
                scenario.getReferenceEvent(),
                scenario.getAssumptionNote());
    }

    private record Assets(long equityAssetKrw, long fxAssetKrw) {
    }

    /**
     * 응답에 싣는 시나리오 요약 (명세 §5.9 {@code scenario} 블록).
     *
     * @param scenarioCode   시나리오 코드
     * @param nameKo         한국어 이름
     * @param referenceEvent 참고한 실제 사건 (없으면 null)
     * @param assumptionNote 적용 순서·가정 설명 (없으면 null)
     */
    public record ScenarioSummary(
            String scenarioCode,
            String nameKo,
            String referenceEvent,
            String assumptionNote
    ) {
    }

    /**
     * 방금 실행한 결과 (명세 §5.9 응답 전체).
     *
     * @param id                 저장된 실행 id
     * @param scenario           적용한 시나리오 요약
     * @param baseDate           계산 기준일
     * @param equityShockPct     적용한 주가 충격률
     * @param fxShockPct         적용한 환율 충격률
     * @param equityAssetKrw     적용 전 해외주식 평가액
     * @param fxAssetBeforeKrw   적용 전 외화자산 평가액
     * @param equityEffectKrw    주가 효과
     * @param fxEffectKrw        환율 효과
     * @param totalEffectKrw     총 평가금액 효과
     * @param fxAssetAfterKrw    적용 후 외화자산 평가액
     * @param interpretationCode 두 효과의 관계 코드
     */
    public record RunView(
            UUID id,
            ScenarioSummary scenario,
            LocalDate baseDate,
            double equityShockPct,
            double fxShockPct,
            long equityAssetKrw,
            long fxAssetBeforeKrw,
            long equityEffectKrw,
            long fxEffectKrw,
            long totalEffectKrw,
            long fxAssetAfterKrw,
            String interpretationCode
    ) {
    }

    /**
     * 과거 실행 한 건 ({@code GET /stress/runs}).
     *
     * @param id              실행 id
     * @param scenario        시나리오 요약 (마스터에서 사라졌으면 null)
     * @param baseDate        계산 기준일
     * @param equityShockPct  실행 시점 주가 충격률 스냅샷
     * @param fxShockPct      실행 시점 환율 충격률 스냅샷
     * @param equityEffectKrw 주가 효과
     * @param fxEffectKrw     환율 효과
     * @param totalEffectKrw  총 평가금액 효과
     * @param createdAt       실행 시각
     */
    public record RunHistoryView(
            UUID id,
            ScenarioSummary scenario,
            LocalDate baseDate,
            double equityShockPct,
            double fxShockPct,
            long equityEffectKrw,
            long fxEffectKrw,
            long totalEffectKrw,
            java.time.Instant createdAt
    ) {
    }
}
