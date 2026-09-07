package com.divurve.domain.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 계획의 예상 원화 비용 범위와 예산 판정 (플래너 명세 §9.3·§9.6·§11.3).
 *
 * <p>비용 세 값은 방향 예측이 아니다 — 같은 외화 금액을 준비할 때 환율 범위에 따라 원화 비용이
 * 얼마나 달라지는지를 나타낼 뿐이다 (명세 §9.3).
 *
 * <p>{@code budgetState} 가 {@code COVERED_IN_RANGE} 여도 <b>목표 달성 보장을 뜻하지 않는다</b>
 * (명세 §9.6). 예산을 초과하는 계획이라도 초과 사실을 숨기지 않는다 (§21-8).
 */
@Embeddable
public class PlanCostSummary {

    @Column(name = "budget_state")
    private String budgetState;

    @Column(name = "low_cost_krw")
    private Long lowCostKrw;

    @Column(name = "base_cost_krw")
    private Long baseCostKrw;

    @Column(name = "high_cost_krw")
    private Long highCostKrw;

    /** JPA 전용 기본 생성자. */
    protected PlanCostSummary() {
    }

    /**
     * 비용 범위와 예산 판정을 만든다.
     *
     * @param budgetState  예산 가능 상태 ({@code BudgetState} 이름)
     * @param lowCostKrw   환율 하단 기준 비용
     * @param baseCostKrw  기준 환율 비용
     * @param highCostKrw  환율 상단 기준 비용
     */
    public static PlanCostSummary of(String budgetState, Long lowCostKrw, Long baseCostKrw, Long highCostKrw) {
        PlanCostSummary summary = new PlanCostSummary();
        summary.budgetState = budgetState;
        summary.lowCostKrw = lowCostKrw;
        summary.baseCostKrw = baseCostKrw;
        summary.highCostKrw = highCostKrw;
        return summary;
    }

    public String getBudgetState() {
        return budgetState;
    }

    public Long getLowCostKrw() {
        return lowCostKrw;
    }

    public Long getBaseCostKrw() {
        return baseCostKrw;
    }

    public Long getHighCostKrw() {
        return highCostKrw;
    }
}
