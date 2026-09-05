package com.divurve.api.dto.me;

/** 사용자 설정 수정 요청 (PUT /me/settings). */
public record SettingsUpdateRequest(
        String defaultBankCode,
        Double fxDiscountRatio,
        String displayMode) {
}
