package com.divurve.api.dto.plan;

/** 회차 완료 기록 응답 (POST /plans/{id}/steps/{seq}/complete). */
public record StepCompleteResponse(
        int seq,
        String status,
        double executedAmount,
        double executedRate,
        double remainingAmount) {
}
