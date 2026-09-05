package com.divurve.api.dto.plan;

import java.util.List;

/**
 * 활성 계획과 회차 응답 (GET /goals/{id}/plans/active).
 * 현재 활성 계획 메타와 각 회차의 진행 상태를 담는다.
 */
public record ActivePlanResponse(
        String id,
        String goalId,
        int version,
        boolean isActive,
        String reason,
        double safeRatio,
        int splitCount,
        double opportunityAmount,
        double opportunityTriggerRate,
        List<PlanResponse.Step> steps) {
}
