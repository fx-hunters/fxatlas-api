package com.divurve.engine.safemode;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 안전모드 6가지 발동조건 평가 (명세 3.9, FR-SF-01).
 * 각 조건별 결과를 독립적으로 판정하며, 부작용·프레임워크 의존 없이 순수하게 동작한다.
 *
 * <p>조건들:
 * 1. 데이터 노후 (Data Staleness) — 마지막 데이터 업데이트 이후 3일 이상 경과
 * 2. 출처 간 차이 초과 (Source Discrepancy) — 서로 다른 출처 환율의 차이가 5% 이상
 * 3. 실제환율 95% 구간 이탈 (Rate Deviation) — 현재 환율이 20일 이평선 ±5% 범위 이탈
 * 4. 변동성 상위 5% (Volatility High) — 최근 30일 변동성이 역사 평균의 125% 이상
 * 5. 마감 14일 이내 미확보율 과다 (Coverage Shortfall) — 미확보율이 20% 이상
 * 6. 연속 건너뛰기 (Consecutive Skip) — 3회 이상 연속 환전 기회 불이행
 */
@EngineComponent
public class SafeModeEvaluator {

    private static final long DATA_STALENESS_DAYS = 3L;
    private static final double SOURCE_DISCREPANCY_THRESHOLD = 0.05; // 5%
    private static final double RATE_DEVIATION_THRESHOLD = 0.05; // ±5%
    private static final double VOLATILITY_HIGH_MULTIPLIER = 1.25; // 125%
    private static final double COVERAGE_SHORTFALL_THRESHOLD = 0.20; // 20%
    private static final long CONSECUTIVE_SKIP_THRESHOLD = 3L;

    /**
     * 6가지 안전모드 조건을 평가한다.
     *
     * @param evaluation 평가 인자 (데이터, 변동성, 환율, 계획 등)
     * @return 안전모드 판정 결과
     * @throws IllegalArgumentException evaluation이 null인 경우
     */
    public SafeModeCheckResult evaluate(SafeModeEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "평가 인자가 null입니다.");

        List<SafeModeCheckResult.Check> checks = new ArrayList<>();
        checks.add(checkDataStaleness(evaluation));
        checks.add(checkSourceDiscrepancy(evaluation));
        checks.add(checkRateDeviation(evaluation));
        checks.add(checkVolatilityHigh(evaluation));
        checks.add(checkCoverageShortfall(evaluation));
        checks.add(checkConsecutiveSkip(evaluation));

        boolean active = checks.stream().anyMatch(check -> !check.passed());
        String status = determineStatus(active, checks);

        return new SafeModeCheckResult(active, status, checks);
    }

    /**
     * 데이터 노후 조건 (3일 이상 미업데이트).
     */
    private SafeModeCheckResult.Check checkDataStaleness(SafeModeEvaluation eval) {
        String key = "data_staleness";
        if (eval.lastUpdateDate() == null) {
            return new SafeModeCheckResult.Check(key, false, "업데이트 이력 없음");
        }
        long daysPassed = java.time.temporal.ChronoUnit.DAYS.between(eval.lastUpdateDate(), LocalDate.now());
        boolean passed = daysPassed < DATA_STALENESS_DAYS;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("마지막 업데이트 이후 %d일 경과", daysPassed));
    }

    /**
     * 출처 간 차이 조건 (5% 이상 차이).
     */
    private SafeModeCheckResult.Check checkSourceDiscrepancy(SafeModeEvaluation eval) {
        String key = "source_discrepancy";
        if (eval.primaryRate() == null || eval.secondaryRate() == null) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        BigDecimal primary = eval.primaryRate();
        BigDecimal secondary = eval.secondaryRate();
        BigDecimal higher = primary.max(secondary);
        BigDecimal lower = primary.min(secondary);
        BigDecimal difference = higher.subtract(lower).divide(lower, 4, java.math.RoundingMode.HALF_UP);

        boolean passed = difference.doubleValue() < SOURCE_DISCREPANCY_THRESHOLD;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("출처 간 차이 %.2f%%", difference.doubleValue() * 100));
    }

    /**
     * 실제환율 95% 구간 이탈 조건 (±5% 범위 이탈).
     */
    private SafeModeCheckResult.Check checkRateDeviation(SafeModeEvaluation eval) {
        String key = "rate_deviation";
        if (eval.currentRate() == null || eval.movingAverageRate() == null) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        BigDecimal current = eval.currentRate();
        BigDecimal movingAverage = eval.movingAverageRate();
        BigDecimal deviation = current.subtract(movingAverage).divide(movingAverage, 4, java.math.RoundingMode.HALF_UP);

        boolean passed = deviation.abs().doubleValue() < RATE_DEVIATION_THRESHOLD;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("환율 편차 %.2f%% 초과", deviation.abs().doubleValue() * 100));
    }

    /**
     * 변동성 상위 5% 조건 (125% 초과).
     */
    private SafeModeCheckResult.Check checkVolatilityHigh(SafeModeEvaluation eval) {
        String key = "volatility_high";
        if (eval.currentVolatility() == null || eval.historicalAverageVolatility() == null) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        double current = eval.currentVolatility().doubleValue();
        double historical = eval.historicalAverageVolatility().doubleValue();

        if (historical <= 0) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        double ratio = current / historical;
        boolean passed = ratio < VOLATILITY_HIGH_MULTIPLIER;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("변동성 비율 %.2f배", ratio));
    }

    /**
     * 마감 14일 이내 미확보율 과다 조건 (20% 이상).
     */
    private SafeModeCheckResult.Check checkCoverageShortfall(SafeModeEvaluation eval) {
        String key = "coverage_shortfall";
        if (eval.daysUntilDeadline() == null || eval.uncoveredRatio() == null) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        long daysRemaining = eval.daysUntilDeadline();
        double uncovered = eval.uncoveredRatio().doubleValue();

        boolean withinDeadline = daysRemaining >= 0 && daysRemaining <= 14;
        boolean passed = !withinDeadline || uncovered < COVERAGE_SHORTFALL_THRESHOLD;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("마감 %d일 남음, 미확보율 %.1f%%", daysRemaining, uncovered * 100));
    }

    /**
     * 연속 건너뛰기 조건 (3회 이상).
     */
    private SafeModeCheckResult.Check checkConsecutiveSkip(SafeModeEvaluation eval) {
        String key = "consecutive_skip";
        if (eval.consecutiveSkipCount() == null) {
            return new SafeModeCheckResult.Check(key, true, null);
        }
        long skips = eval.consecutiveSkipCount();
        boolean passed = skips < CONSECUTIVE_SKIP_THRESHOLD;
        return new SafeModeCheckResult.Check(
            key,
            passed,
            passed ? null : String.format("%d회 연속 기회 불이행", skips));
    }

    /**
     * 평가 결과로부터 상태 라벨을 결정한다.
     * - NORMAL: 모든 조건 통과
     * - CAUTION: 1~2개 조건 위반
     * - SAFE_MODE: 3개 이상 조건 위반 또는 critical 조건 위반
     */
    private String determineStatus(boolean active, List<SafeModeCheckResult.Check> checks) {
        if (!active) {
            return "normal";
        }
        long violationCount = checks.stream().filter(c -> !c.passed()).count();
        if (violationCount >= 3) {
            return "safe_mode";
        }
        return "caution";
    }

}
