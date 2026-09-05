package com.divurve.api.dto.me;

/**
 * 사용자 설정 응답 (GET /me/settings).
 * 필드명은 명세 그대로 — {@code default_bank_code} · {@code fx_discount_ratio} · {@code display_mode}.
 */
public record SettingsResponse(
        String defaultBankCode,
        double fxDiscountRatio,
        String displayMode) {
}
