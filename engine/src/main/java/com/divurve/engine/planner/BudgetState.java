package com.divurve.engine.planner;

/**
 * 예산 가능 상태 (플래너 명세 §9.6).
 *
 * <p><b>{@code COVERED_IN_RANGE} 는 목표 달성 보장을 뜻하지 않는다</b> — 명세 §9.6 이 명시한다.
 * 현재 환율 범위 안에서는 예산으로 감당된다는 조건부 판정일 뿐이다.
 */
public enum BudgetState {

    /** {@code availableBudget >= highCost} — 현재 예산 범위에서 준비 가능. */
    COVERED_IN_RANGE,

    /** {@code lowCost <= availableBudget < highCost} — 환율 범위에 따라 예산 조정 가능성 있음. */
    RANGE_SENSITIVE,

    /** {@code availableBudget < lowCost} — 금액·날짜·예산 중 하나를 조정해야 함. */
    CONSTRAINT_ADJUSTMENT_REQUIRED,

    /** 예산 미입력 — 비용 범위만 표시하고 가능 여부는 판정하지 않는다. */
    BUDGET_NOT_PROVIDED
}
