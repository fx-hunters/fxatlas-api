package com.divurve.api.dto.me;

/**
 * 사용자 설정 수정 요청 (PUT /me/settings). null 필드는 기존값을 유지한다.
 * {@code explain_level}(설명 선호 3단계)·{@code explain_domain}(익숙한 설명 분야)은 표현에만 쓰인다(FR-MY-03).
 */
public record SettingsUpdateRequest(
        String defaultBankCode,
        Double fxDiscountRatio,
        String explainLevel,
        String explainDomain) {
}
