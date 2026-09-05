package com.divurve.api.dto.plan;

/**
 * 계획 확정·저장 요청 (POST /goals/{id}/plans).
 * 미리보기에서 확정한 파라미터를 그대로 저장한다.
 */
public record PlanCreateRequest(
        long weeklyBudgetKrw,
        double safeRatio,
        int splitCount) {
}
