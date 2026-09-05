package com.divurve.api.dto.plan;

/**
 * 회차 완료 기록 요청 (POST /plans/{id}/steps/{seq}/complete).
 * 실제 체결된 외화 금액과 환율을 기록한다.
 */
public record StepCompleteRequest(
        double executedAmount,
        double executedRate) {
}
