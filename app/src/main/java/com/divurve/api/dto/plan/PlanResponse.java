package com.divurve.api.dto.plan;

import java.util.List;

/**
 * 계획 응답 (POST /goals/{id}/plans 확정, GET /goals/{id}/plans/active).
 * opportunity 는 회차가 아니라 단일 대기 물량으로 저장된다.
 */
public record PlanResponse(
        String id,
        String goalId,
        int version,
        boolean isActive,
        String reason,
        double safeRatio,
        int splitCount,
        double opportunityAmount,
        double opportunityTriggerRate,
        List<Step> steps) {

    /** 저장된 회차. */
    public record Step(
            int seq,
            String scheduledDate,
            double amount,
            double executedAmount,
            String status) {
    }
}
