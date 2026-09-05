package com.divurve.api.dto.goal;

import java.time.LocalDate;

/** 목표 수정 요청 (PUT /goals/{id}). 변경할 필드만 담는다. */
public record GoalUpdateRequest(
        String name,
        Double targetAmount,
        LocalDate targetDate,
        Long budgetAmount,
        String budgetPeriod,
        Boolean isSpeculative) {
}
