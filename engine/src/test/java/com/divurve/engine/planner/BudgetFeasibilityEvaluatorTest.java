package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link BudgetFeasibilityEvaluator} 테스트 — 예산 가능 판정 (명세 §9.6·§21-8).
 *
 * <p><b>§21-8</b> 예산을 초과하는 계획은 초과 사실을 숨기지 않는다 — 판정만 하고 보정하지 않는다.
 */
@DisplayName("BudgetFeasibilityEvaluator")
class BudgetFeasibilityEvaluatorTest {

    private static final CostRange COST = new CostRange(1_338_257L, 1_367_258L, 1_396_882L);

    private final BudgetFeasibilityEvaluator evaluator = new BudgetFeasibilityEvaluator();

    @ParameterizedTest(name = "예산 {0} → {1}")
    @CsvSource({
            // 상단 비용 이상이면 환율이 어디로 가도 감당된다
            "2000000, COVERED_IN_RANGE",
            "1396882, COVERED_IN_RANGE",
            // 하단 이상 상단 미만이면 환율에 따라 갈린다
            "1396881, RANGE_SENSITIVE",
            "1367258, RANGE_SENSITIVE",
            "1338257, RANGE_SENSITIVE",
            // 하단 비용에도 못 미치면 조정이 필요하다
            "1338256, CONSTRAINT_ADJUSTMENT_REQUIRED",
            "0,       CONSTRAINT_ADJUSTMENT_REQUIRED",
    })
    @DisplayName("예산과 비용 범위를 비교해 상태를 정한다 (§9.6)")
    void evaluatesAgainstCostRange(long budgetKrw, BudgetState expected) {
        assertThat(evaluator.evaluate(budgetKrw, COST)).isEqualTo(expected);
    }

    @Test
    @DisplayName("예산 미입력이면 판정하지 않는다 — 비용 범위만 표시한다")
    void nullBudgetIsNotProvided() {
        assertThat(evaluator.evaluate(null, COST)).isEqualTo(BudgetState.BUDGET_NOT_PROVIDED);
    }

    @Test
    @DisplayName("COVERED_IN_RANGE 는 목표 달성 보장이 아니다 — 조건부 판정이다")
    void coveredInRangeIsConditional() {
        // 명세 §9.6 이 명시한다. 상단 비용을 넘는 예산이어도 환율이 범위를 벗어나면 달라진다.
        BudgetState state = evaluator.evaluate(COST.highKrw(), COST);

        assertThat(state).isEqualTo(BudgetState.COVERED_IN_RANGE);
        assertThat(state).isNotEqualTo(BudgetState.RANGE_SENSITIVE);
    }

    @Test
    @DisplayName("비용 범위가 한 점이면 경계에서 COVERED_IN_RANGE 다")
    void degenerateCostRange() {
        CostRange flat = new CostRange(1000L, 1000L, 1000L);

        assertThat(evaluator.evaluate(1000L, flat)).isEqualTo(BudgetState.COVERED_IN_RANGE);
        assertThat(evaluator.evaluate(999L, flat)).isEqualTo(BudgetState.CONSTRAINT_ADJUSTMENT_REQUIRED);
    }

    @Test
    @DisplayName("null 비용 범위는 거부한다")
    void rejectsNullCostRange() {
        assertThatNullPointerException()
                .isThrownBy(() -> evaluator.evaluate(1000L, null))
                .withMessage("costRange");
    }

    @Test
    @DisplayName("valueOf 로도 모든 상태에 접근할 수 있다")
    void valueOfCoversAllStates() {
        for (BudgetState state : BudgetState.values()) {
            assertThat(BudgetState.valueOf(state.name())).isSameAs(state);
        }
    }
}
