package com.divurve.api.dto.goal;

import java.time.LocalDate;

/**
 * 목표 생성 요청 (POST /goals, 명세 3.2).
 * {@code kind} 가 recurring 이면 {@code purpose} 는 invest 만 허용한다.
 * {@code held_amount} 는 받지 않는다 — 서버가 /deposits 에서 조회한다 (FR-RT-05).
 */
public record GoalCreateRequest(
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
        boolean isSpeculative) {
}
