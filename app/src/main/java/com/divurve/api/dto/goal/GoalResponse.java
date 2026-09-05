package com.divurve.api.dto.goal;

import java.time.LocalDate;

/**
 * 목표 상세/생성 응답 (GET /goals/{id}, POST /goals, 명세 3.2).
 * {@code heldAmount} 는 서버가 /deposits 에서 조회해 채운다. {@code suggested} 는 권장 슬라이더 기본값.
 */
public record GoalResponse(
        String id,
        String name,
        String kind,
        String purpose,
        String currencyCode,
        double targetAmount,
        LocalDate targetDate,
        String recurInterval,
        long budgetAmount,
        String budgetCurrencyCode,
        String budgetPeriod,
        boolean isSpeculative,
        String status,
        double heldAmount,
        Suggested suggested) {

    /** 목적·성향·변동성으로 산출한 권장 값. */
    public record Suggested(double safeRatio, double floor, int splitCount) {
    }
}
