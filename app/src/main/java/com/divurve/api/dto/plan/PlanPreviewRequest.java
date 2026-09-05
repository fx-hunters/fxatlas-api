package com.divurve.api.dto.plan;

/**
 * 계획 미리보기 요청 (POST /plans/preview, 명세 3.1).
 * {@code safeRatio}·{@code splitCount} 를 생략(null)하면 서버가 목적·성향·변동성으로 권장값을 산출한다.
 */
public record PlanPreviewRequest(
        String goalId,
        long weeklyBudgetKrw,
        Double safeRatio,
        Integer splitCount) {
}
