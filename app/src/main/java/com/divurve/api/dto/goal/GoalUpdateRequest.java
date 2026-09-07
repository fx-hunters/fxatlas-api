package com.divurve.api.dto.goal;

import java.time.LocalDate;

/**
 * 목표 수정 요청 (PUT /goals/{id}). 변경할 필드만 담는다 — 부분 갱신 계약이므로 모든 필드가
 * {@code null} 을 "값 변경 없음"으로 허용해야 한다({@code @NotNull} 을 달면 정상 요청이 막힌다).
 *
 * <p>{@code targetAmount}(0 이하 금지)·{@code targetDate}(과거 금지)·{@code name}(공백 금지)은
 * 값이 있을 때만 검증한다. 세 가지 모두 {@code GoalService.update} 에서 null 이 아닐 때만 확인하고,
 * {@code field} 에 스네이크케이스 문자열을 직접 써서 응답한다 — 생성 경로와 같은 규칙을 한곳에서
 * 관리하기 위해서다({@link GoalCreateRequest} 주석 참고).
 */
public record GoalUpdateRequest(
        String name,
        Double targetAmount,
        LocalDate targetDate,
        Long budgetAmount,
        String budgetPeriod,
        Boolean isSpeculative) {
}
