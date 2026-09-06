package com.divurve.engine.safemode;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SafeModeEvaluator 테스트")
class SafeModeEvaluatorTest {

    private SafeModeEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SafeModeEvaluator();
    }

    @Test
    @DisplayName("evaluate null 인자 시 예외 던지기")
    void testEvaluateNullThrowsException() {
        assertThrows(NullPointerException.class, () -> evaluator.evaluate(null));
    }

    @Test
    @DisplayName("모든 조건 통과 시 상태 normal")
    void testAllPassedReturnNormal() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertFalse(result.active());
        assertEquals("normal", result.status());
        assertTrue(result.checks().stream().allMatch(SafeModeCheckResult.Check::passed));
    }

    @Test
    @DisplayName("데이터 노후 조건 발동 (3일 이상 경과)")
    void testDataStalenessTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now().minusDays(4))
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        assertEquals("caution", result.status());
        var staleness = result.checks().stream()
            .filter(c -> "data_staleness".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(staleness.passed());
        assertTrue(staleness.reason().contains("4일"));
    }

    @Test
    @DisplayName("출처 간 차이 조건 발동 (5% 이상)")
    void testSourceDiscrepancyTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1000"))
            .secondaryRate(new BigDecimal("1100"))
            .currentRate(new BigDecimal("1050"))
            .movingAverageRate(new BigDecimal("1050"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        var discrepancy = result.checks().stream()
            .filter(c -> "source_discrepancy".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(discrepancy.passed());
    }

    @Test
    @DisplayName("실제환율 이탈 조건 발동 (±5% 이상)")
    void testRateDeviationTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1300"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        var deviation = result.checks().stream()
            .filter(c -> "rate_deviation".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(deviation.passed());
    }

    @Test
    @DisplayName("변동성 조건 발동 (125% 초과)")
    void testVolatilityHighTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.015"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        var volatility = result.checks().stream()
            .filter(c -> "volatility_high".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(volatility.passed());
    }

    @Test
    @DisplayName("미확보율 조건 발동 (마감 14일 이내, 20% 이상)")
    void testCoverageShortfallTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(7L)
            .uncoveredRatio(new BigDecimal("0.25"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(coverage.passed());
    }

    @Test
    @DisplayName("연속 건너뛰기 조건 발동 (3회 이상)")
    void testConsecutiveSkipTriggered() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(5L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        var skip = result.checks().stream()
            .filter(c -> "consecutive_skip".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(skip.passed());
    }

    @Test
    @DisplayName("3개 이상 조건 위반 시 safe_mode 상태")
    void testThreeOrMoreViolationsReturnSafeMode() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now().minusDays(5))
            .primaryRate(new BigDecimal("1000"))
            .secondaryRate(new BigDecimal("1100"))
            .currentRate(new BigDecimal("1300"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.015"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(7L)
            .uncoveredRatio(new BigDecimal("0.25"))
            .consecutiveSkipCount(5L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        assertEquals("safe_mode", result.status());
        long violationCount = result.checks().stream()
            .filter(c -> !c.passed())
            .count();
        assertTrue(violationCount >= 3);
    }

    @Test
    @DisplayName("1-2개 조건 위반 시 caution 상태")
    void testOneOrTwoViolationsReturnCaution() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now().minusDays(4))
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertTrue(result.active());
        assertEquals("caution", result.status());
        long violationCount = result.checks().stream()
            .filter(c -> !c.passed())
            .count();
        assertTrue(violationCount >= 1 && violationCount <= 2);
    }

    @Test
    @DisplayName("null 데이터는 조건 통과로 취급")
    void testNullDataTreatedAsPass() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(null)
            .secondaryRate(null)
            .currentRate(null)
            .movingAverageRate(null)
            .currentVolatility(null)
            .historicalAverageVolatility(null)
            .daysUntilDeadline(null)
            .uncoveredRatio(null)
            .consecutiveSkipCount(null)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        assertFalse(result.active());
        assertEquals("normal", result.status());
    }

    @Test
    @DisplayName("마감 14일 이후의 미확보율은 조건 미발동")
    void testCoverageShortfallNotTriggeredAfterDeadline() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(20L)
            .uncoveredRatio(new BigDecimal("0.50"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(coverage.passed());
    }

    @Test
    @DisplayName("변동성 0 인 경우 조건 통과")
    void testZeroVolatilityTreatedAsPass() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.02"))
            .historicalAverageVolatility(new BigDecimal("0.00"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var volatility = result.checks().stream()
            .filter(c -> "volatility_high".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(volatility.passed());
    }

    @Test
    @DisplayName("데이터 노후 조건 - lastUpdateDate null 처리")
    void testDataStalenessWithNullLastUpdateDate() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(null)
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var staleness = result.checks().stream()
            .filter(c -> "data_staleness".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertFalse(staleness.passed());
        assertTrue(staleness.reason().contains("업데이트 이력"));
    }

    @Test
    @DisplayName("출처 간 차이 조건 - primaryRate만 null")
    void testSourceDiscrepancyWithNullPrimaryRate() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(null)
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var discrepancy = result.checks().stream()
            .filter(c -> "source_discrepancy".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(discrepancy.passed());
    }

    @Test
    @DisplayName("출처 간 차이 조건 - secondaryRate만 null")
    void testSourceDiscrepancyWithNullSecondaryRate() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(null)
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var discrepancy = result.checks().stream()
            .filter(c -> "source_discrepancy".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(discrepancy.passed());
    }

    @Test
    @DisplayName("실제환율 이탈 조건 - currentRate만 null")
    void testRateDeviationWithNullCurrentRate() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(null)
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var deviation = result.checks().stream()
            .filter(c -> "rate_deviation".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(deviation.passed());
    }

    @Test
    @DisplayName("실제환율 이탈 조건 - movingAverageRate만 null")
    void testRateDeviationWithNullMovingAverageRate() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(null)
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var deviation = result.checks().stream()
            .filter(c -> "rate_deviation".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(deviation.passed());
    }

    @Test
    @DisplayName("변동성 조건 - currentVolatility만 null")
    void testVolatilityHighWithNullCurrentVolatility() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(null)
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var volatility = result.checks().stream()
            .filter(c -> "volatility_high".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(volatility.passed());
    }

    @Test
    @DisplayName("변동성 조건 - historicalAverageVolatility만 null")
    void testVolatilityHighWithNullHistoricalAverageVolatility() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(null)
            .daysUntilDeadline(30L)
            .uncoveredRatio(new BigDecimal("0.05"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var volatility = result.checks().stream()
            .filter(c -> "volatility_high".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(volatility.passed());
    }

    @Test
    @DisplayName("미확보율 조건 - daysUntilDeadline만 null")
    void testCoverageShortfallWithNullDaysUntilDeadline() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(null)
            .uncoveredRatio(new BigDecimal("0.25"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(coverage.passed());
    }

    @Test
    @DisplayName("미확보율 조건 - uncoveredRatio만 null")
    void testCoverageShortfallWithNullUncoveredRatio() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(7L)
            .uncoveredRatio(null)
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(coverage.passed());
    }

    @Test
    @DisplayName("미확보율 조건 - 마감 전, 미확보율 미달")
    void testCoverageShortfallBeforeDeadlineWithLowRatio() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(10L)
            .uncoveredRatio(new BigDecimal("0.10"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(coverage.passed());
    }

    @Test
    @DisplayName("미확보율 조건 - 음수 일수 처리")
    void testCoverageShortfallWithNegativeDays() {
        SafeModeEvaluation eval = SafeModeEvaluation.builder()
            .lastUpdateDate(LocalDate.now())
            .primaryRate(new BigDecimal("1200"))
            .secondaryRate(new BigDecimal("1210"))
            .currentRate(new BigDecimal("1205"))
            .movingAverageRate(new BigDecimal("1200"))
            .currentVolatility(new BigDecimal("0.01"))
            .historicalAverageVolatility(new BigDecimal("0.01"))
            .daysUntilDeadline(-5L)
            .uncoveredRatio(new BigDecimal("0.50"))
            .consecutiveSkipCount(1L)
            .build();

        SafeModeCheckResult result = evaluator.evaluate(eval);

        var coverage = result.checks().stream()
            .filter(c -> "coverage_shortfall".equals(c.key()))
            .findFirst()
            .orElseThrow();
        assertTrue(coverage.passed());
    }
}
