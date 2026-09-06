package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.util.Objects;

/**
 * 예산으로 계획을 감당할 수 있는지 판정한다 (플래너 명세 §9.6).
 *
 * <p>불변조건 §21-8 — <b>예산을 초과하는 계획은 초과 사실을 숨기지 않는다.</b> 그래서 판정만
 * 하고 금액·날짜·예산을 임의로 보정하지 않는다. 조정이 필요하면 선택지를 제시하는 것은
 * 호출부(§15·§17)의 몫이다.
 */
@EngineComponent
public class BudgetFeasibilityEvaluator {

    /**
     * 예산 가능 상태를 판정한다.
     *
     * @param availableBudgetKrw 목표일까지 사용 가능한 예산 (원). {@code null} 이면 미입력
     * @param costRange          환율 범위별 예상 비용
     * @return 명세 §9.6 의 상태 코드
     */
    public BudgetState evaluate(Long availableBudgetKrw, CostRange costRange) {
        Objects.requireNonNull(costRange, "costRange");
        if (availableBudgetKrw == null) {
            return BudgetState.BUDGET_NOT_PROVIDED;
        }
        if (availableBudgetKrw >= costRange.highKrw()) {
            return BudgetState.COVERED_IN_RANGE;
        }
        if (availableBudgetKrw >= costRange.lowKrw()) {
            return BudgetState.RANGE_SENSITIVE;
        }
        return BudgetState.CONSTRAINT_ADJUSTMENT_REQUIRED;
    }
}
