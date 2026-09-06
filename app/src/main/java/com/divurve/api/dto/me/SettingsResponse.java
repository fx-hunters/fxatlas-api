package com.divurve.api.dto.me;

/**
 * 사용자 설정 응답 (GET/PUT /me/settings).
 * 필드명은 명세 그대로 — {@code default_bank_code} · {@code fx_discount_ratio} · {@code explain_level} · {@code explain_domain}.
 * {@code base_spread_ratio}(은행 기본 스프레드) · {@code effective_spread_ratio}(우대율 적용 실효 스프레드)는
 * 실효 스프레드 계산·표시(FR-MY-04) 결과다.
 */
public record SettingsResponse(
        String defaultBankCode,
        double fxDiscountRatio,
        String explainLevel,
        String explainDomain,
        double baseSpreadRatio,
        double effectiveSpreadRatio) {
}
