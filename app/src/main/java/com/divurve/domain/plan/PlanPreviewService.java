package com.divurve.domain.plan;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.ForbiddenException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.engine.bucket.BucketAllocator;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.cost.CostCalculator;
import com.divurve.engine.split.SplitVarianceReducer;
import com.divurve.engine.simulate.MonteCarloSimulator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 계획 미리보기 엔진 (이슈 #18, 명세 3.1).
 * 목표 정보·예산·투자성향으로부터 회차 계획을 미리보기한다. 저장하지 않는다.
 *
 * <p>핵심 책임:
 * - 투기/레버리지 목적 판별 (403)
 * - 버킷 비율 산출 (목적별 하한 적용)
 * - 분할 횟수·간격 결정
 * - 회차 스케줄 생성
 * - 달성 확률 계산 (몬테카를로)
 * - 비용 비교 (전략별)
 * - 집중도 변화 시뮬레이션
 */
@UseCase
public class PlanPreviewService {

    private final GoalRepository goalRepository;
    private final BucketAllocator bucketAllocator;
    private final SplitVarianceReducer splitVarianceReducer;
    private final CostCalculator costCalculator;
    private final MonteCarloSimulator monteCarloSimulator;
    private final ConcentrationCalculator concentrationCalculator;

    public PlanPreviewService(GoalRepository goalRepository,
            BucketAllocator bucketAllocator,
            SplitVarianceReducer splitVarianceReducer,
            CostCalculator costCalculator,
            MonteCarloSimulator monteCarloSimulator,
            ConcentrationCalculator concentrationCalculator) {
        this.goalRepository = Objects.requireNonNull(goalRepository, "GoalRepository는 null일 수 없습니다");
        this.bucketAllocator = Objects.requireNonNull(bucketAllocator, "BucketAllocator는 null일 수 없습니다");
        this.splitVarianceReducer = Objects.requireNonNull(splitVarianceReducer,
                "SplitVarianceReducer는 null일 수 없습니다");
        this.costCalculator = Objects.requireNonNull(costCalculator, "CostCalculator는 null일 수 없습니다");
        this.monteCarloSimulator = Objects.requireNonNull(monteCarloSimulator,
                "MonteCarloSimulator는 null일 수 없습니다");
        this.concentrationCalculator = Objects.requireNonNull(concentrationCalculator,
                "ConcentrationCalculator는 null일 수 없습니다");
    }

    /**
     * 계획 미리보기를 생성한다.
     *
     * @param goalIdStr Goal ID (문자열)
     * @param weeklyBudgetKrw 주간 예산 (KRW)
     * @param safeRatio 안전 비율 (null이면 권장값 사용)
     * @param splitCount 분할 횟수 (null이면 권장값 사용)
     * @return 미리보기 응답
     * @throws ForbiddenException 투기/레버리지 목적인 경우
     * @throws NotFoundException 목표를 찾을 수 없는 경우
     */
    public PlanPreviewInfo generatePreview(String goalIdStr, long weeklyBudgetKrw, Double safeRatio,
            Integer splitCount) {
        UUID goalId = parseGoalId(goalIdStr);
        Goal goal = findGoal(goalId);

        validateNotSpeculative(goal);

        double effectiveSafeRatio = resolveOrValidateSafeRatio(goal.getPurpose(), safeRatio);
        int effectiveSplitCount = resolveOrValidateSplitCount(splitCount);

        return calculatePreview(goal, weeklyBudgetKrw, effectiveSafeRatio, effectiveSplitCount);
    }

    private UUID parseGoalId(String goalIdStr) {
        try {
            return UUID.fromString(goalIdStr);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("유효한 목표 ID가 아닙니다: " + goalIdStr);
        }
    }

    private Goal findGoal(UUID goalId) {
        return goalRepository.findById(goalId)
                .orElseThrow(() -> new NotFoundException("목표를 찾을 수 없습니다: " + goalId));
    }

    private void validateNotSpeculative(Goal goal) {
        if (goal.isSpeculative()) {
            throw new ForbiddenException("SPECULATIVE_PURPOSE_BLOCKED",
                    "투기성 목적의 계획은 생성할 수 없습니다");
        }
    }

    private double resolveOrValidateSafeRatio(String purpose, Double safeRatio) {
        if (safeRatio == null) {
            return bucketAllocator.getDefaultSafeRatio();
        }

        if (!bucketAllocator.isSafeRatioValid(purpose, safeRatio)) {
            double floor = bucketAllocator.getSafeRatioFloor(purpose);
            throw new IllegalArgumentException("안전 비율이 목적의 하한(" + floor + ")을 만족하지 않습니다");
        }

        return safeRatio;
    }

    private int resolveOrValidateSplitCount(Integer splitCount) {
        if (splitCount == null) {
            return 4; // 권장 분할 횟수
        }

        if (splitCount < 1 || splitCount > 52) {
            throw new IllegalArgumentException("분할 횟수는 1~52 범위여야 합니다");
        }

        return splitCount;
    }

    private PlanPreviewInfo calculatePreview(Goal goal, long weeklyBudgetKrw, double safeRatio,
            int splitCount) {
        // 기본 계산
        long monthlyBudgetKrw = weeklyBudgetKrw * 4; // 간단한 근사
        long safeAmountKrw = Math.round(monthlyBudgetKrw * safeRatio);
        long opportunityAmountKrw = Math.round(monthlyBudgetKrw * (1.0 - safeRatio));

        // 회차 정보
        int intervalDays = calculateIntervalDays(goal.getRecurInterval(), splitCount);
        List<PlanPreviewInfo.Step> steps = generateSchedule(safeAmountKrw, splitCount, intervalDays);

        // 분할 계수
        double gFactor = splitVarianceReducer.gFactor(splitCount);
        double nextSigmaGain = splitCount < 52 ? splitVarianceReducer.sigmaGain(splitCount + 1) : 0.0;
        long nextFeeIncrease = costCalculator.fixedCost(1, 3000); // 임시값

        // 비용
        double effectiveSpreadRatio = 0.0035; // 임시값
        long totalCostKrw = costCalculator.totalCost(safeAmountKrw, effectiveSpreadRatio, splitCount, 3000);

        // 달성 확률
        double achieveProb = monteCarloSimulator.achievementProbability(
                0.08,   // 기대 수익률 8%
                0.15,   // 변동성 15%
                0.0,    // 초기 보유액
                monthlyBudgetKrw,
                calculateMonths(goal.getTargetDate()),
                goal.getTargetAmount(),
                System.currentTimeMillis()
        );

        // 응답 생성
        return new PlanPreviewInfo(
                new PlanPreviewInfo.GoalSummary(goal.getKind(), goal.getPurpose(), goal.getCurrencyCode()),
                0.0, // unfunded (임시)
                calculateMonths(goal.getTargetDate()),
                0.15, // sigmaHorizon (임시)
                new PlanPreviewInfo.Buckets(safeAmountKrw, opportunityAmountKrw, safeRatio,
                        bucketAllocator.getSafeRatioFloor(goal.getPurpose())),
                new PlanPreviewInfo.Split(splitCount, intervalDays, gFactor,
                        new PlanPreviewInfo.Split.NextStepDelta(nextSigmaGain, nextFeeIncrease)),
                steps,
                new PlanPreviewInfo.Opportunity(opportunityAmountKrw, 1.05, // triggerRate
                        formatTargetDate(goal.getTargetDate()), "기회 상황에서만 실행"),
                new PlanPreviewInfo.Metrics(
                        "achieveProb", // hero
                        0.05, // entrySigma (임시)
                        0.06, // entrySigmaOnce (임시)
                        achieveProb,
                        achieveProb,
                        0.95, // worst5Rate (임시)
                        new PlanPreviewInfo.Metrics.Fee(
                                costCalculator.spreadCost(safeAmountKrw, effectiveSpreadRatio),
                                costCalculator.fixedCost(splitCount, 3000),
                                totalCostKrw
                        )
                ),
                generateComparisons(safeAmountKrw, splitCount),
                new PlanPreviewInfo.Concentration(
                        new HashMap<>(),
                        new HashMap<>(),
                        0.02,
                        "neutral"
                ),
                new ArrayList<>()
        );
    }

    private int calculateIntervalDays(String recurInterval, int splitCount) {
        if (recurInterval == null || recurInterval.isBlank()) {
            return 365 / splitCount; // 1년 분할
        }

        return switch (recurInterval) {
            case "WEEKLY" -> 7;
            case "BIWEEKLY" -> 14;
            case "MONTHLY" -> 30;
            case "QUARTERLY" -> 90;
            default -> 365 / splitCount;
        };
    }

    /**
     * 기회 버킷의 최종 안전 환전일 표기. target_date 는 nullable 이므로
     * ({@link #calculateMonths} 도 null 을 12개월로 가정한다) 여기서도 null 을 그대로 흘려보낸다.
     */
    private String formatTargetDate(LocalDate targetDate) {
        return targetDate == null ? null : targetDate.toString();
    }

    private int calculateMonths(LocalDate targetDate) {
        if (targetDate == null) {
            return 12;
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), targetDate);
        return (int) (days / 30);
    }

    private List<PlanPreviewInfo.Step> generateSchedule(long safeAmountKrw, int splitCount, int intervalDays) {
        List<PlanPreviewInfo.Step> steps = new ArrayList<>();
        double stepAmount = safeAmountKrw / splitCount;
        LocalDate currentDate = LocalDate.now();

        for (int i = 1; i <= splitCount; i++) {
            LocalDate scheduledDate = currentDate.plusDays((long) intervalDays * (i - 1));
            steps.add(new PlanPreviewInfo.Step(
                    i,
                    scheduledDate.toString(),
                    stepAmount,
                    Math.round(stepAmount),
                    0.0,
                    "PENDING"
            ));
        }

        return steps;
    }

    private List<PlanPreviewInfo.Comparison> generateComparisons(long safeAmountKrw, int splitCount) {
        List<PlanPreviewInfo.Comparison> comparisons = new ArrayList<>();

        // 일시환전 (1회)
        long onceCost = costCalculator.totalCost(safeAmountKrw, 0.0035, 1, 3000);
        comparisons.add(new PlanPreviewInfo.Comparison(
                "LUMP_SUM",
                1,
                1.0,
                0.92,
                onceCost,
                0.45
        ));

        // 균등분할 (현재 분할)
        long currentCost = costCalculator.totalCost(safeAmountKrw, 0.0035, splitCount, 3000);
        comparisons.add(new PlanPreviewInfo.Comparison(
                "EQUAL_SPLIT",
                splitCount,
                1.0,
                0.95,
                currentCost,
                0.65
        ));

        // 권장 분할 (8회)
        long recommendedCost = costCalculator.totalCost(safeAmountKrw, 0.0035, 8, 3000);
        comparisons.add(new PlanPreviewInfo.Comparison(
                "RECOMMENDED",
                8,
                1.0,
                0.96,
                recommendedCost,
                0.68
        ));

        return comparisons;
    }

    /**
     * 미리보기 응답 정보를 담는 내부 레코드.
     */
    public record PlanPreviewInfo(
            GoalSummary goal,
            double unfunded,
            int weeks,
            double sigmaHorizon,
            Buckets buckets,
            Split split,
            List<Step> steps,
            Opportunity opportunity,
            Metrics metrics,
            List<Comparison> comparison,
            Concentration concentration,
            List<Warning> warnings) {

        public record GoalSummary(String kind, String purpose, String currencyCode) {
        }

        public record Buckets(double safe, double opportunity, double safeRatio, double floor) {
        }

        public record Split(
                int count,
                int intervalDays,
                double gFactor,
                NextStepDelta nextStepDelta) {

            public record NextStepDelta(double sigmaGain, long feeIncreaseKrw) {
            }
        }

        public record Step(
                int seq,
                String scheduledDate,
                double amount,
                long krwEstimate,
                double executedAmount,
                String status) {
        }

        public record Opportunity(
                double amount,
                double triggerRate,
                String finalSafeDate,
                String note) {
        }

        public record Metrics(
                String hero,
                double entrySigma,
                double entrySigmaOnce,
                double achieveProb,
                double achieveProbOnce,
                double worst5Rate,
                Fee fee) {

            public record Fee(long spreadKrw, long fixedKrw, long totalKrw) {
            }
        }

        public record Comparison(
                String strategy,
                Integer splitCount,
                double avgRate,
                double worst5Rate,
                long feeKrw,
                double achieveProb) {
        }

        public record Concentration(
                Map<String, Double> before,
                Map<String, Double> after,
                double threshold,
                String verdict) {
        }

        public record Warning(String code, String message) {
        }
    }
}
