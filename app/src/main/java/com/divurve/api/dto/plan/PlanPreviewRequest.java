package com.divurve.api.dto.plan;

import jakarta.validation.constraints.NotBlank;

/**
 * 계획 미리보기 요청 (POST /plans/preview, 명세 3.1).
 * {@code safeRatio}·{@code splitCount} 를 생략(null)하면 서버가 목적·성향·변동성으로 권장값을 산출한다.
 *
 * <p>{@code goalId} 가 없으면 컨트롤러가 {@code UUID.fromString(null)} 을 호출해 500 으로 샜다
 * (이슈 #75) — {@code @NotBlank} 로 컨트롤러 진입 전에 400 {@code VALIDATION_FAILED} 로 막는다.
 */
public record PlanPreviewRequest(
        @NotBlank(message = "목표 ID는 필수입니다.") String goalId,
        long weeklyBudgetKrw,
        Double safeRatio,
        Integer splitCount) {
}
