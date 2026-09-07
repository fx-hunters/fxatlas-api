package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetFeasibilityEvaluator} — 예산 가능 상태 판정 (플래너 명세 §9.6).
 */
@DisplayName("BudgetFeasibilityEvaluator")
class BudgetFeasibilityEvaluatorTest {

    private static final CostRange COSTS = new CostRange(1_300_000L, 1_350_000L, 1_400_000L);

    private BudgetFeasibilityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BudgetFeasibilityEvaluator();
    }

    @Test
    @DisplayName("예산이 상단 비용 이상이면 범위 안에서 준비 가능하다")
    void evaluate_BudgetAboveHighCost_ReturnsCovered() {
        assertThat(evaluator.evaluate(1_500_000L, COSTS)).isEqualTo(BudgetState.COVERED_IN_RANGE);
    }

    @Test
    @DisplayName("예산이 상단 비용과 같으면 준비 가능하다 — 경계 포함")
    void evaluate_BudgetEqualsHighCost_ReturnsCovered() {
        assertThat(evaluator.evaluate(1_400_000L, COSTS)).isEqualTo(BudgetState.COVERED_IN_RANGE);
    }

    @Test
    @DisplayName("예산이 하단과 상단 사이면 환율에 민감하다")
    void evaluate_BudgetBetween_ReturnsRangeSensitive() {
        assertThat(evaluator.evaluate(1_350_000L, COSTS)).isEqualTo(BudgetState.RANGE_SENSITIVE);
    }

    @Test
    @DisplayName("예산이 하단 비용과 같으면 환율에 민감하다 — 경계 포함")
    void evaluate_BudgetEqualsLowCost_ReturnsRangeSensitive() {
        assertThat(evaluator.evaluate(1_300_000L, COSTS)).isEqualTo(BudgetState.RANGE_SENSITIVE);
    }

    @Test
    @DisplayName("예산이 하단 비용에 못 미치면 조정이 필요하다")
    void evaluate_BudgetBelowLowCost_ReturnsAdjustmentRequired() {
        assertThat(evaluator.evaluate(1_299_999L, COSTS))
                .isEqualTo(BudgetState.CONSTRAINT_ADJUSTMENT_REQUIRED);
    }

    @Test
    @DisplayName("예산 미입력이면 가능 여부를 판정하지 않는다")
    void evaluate_NullBudget_ReturnsNotProvided() {
        assertThat(evaluator.evaluate(null, COSTS)).isEqualTo(BudgetState.BUDGET_NOT_PROVIDED);
    }

    @Test
    @DisplayName("비용 범위가 null 이면 거부한다")
    void evaluate_NullCostRange_Throws() {
        assertThatThrownBy(() -> evaluator.evaluate(1_000_000L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
