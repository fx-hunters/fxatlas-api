package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 설정 수정 요청 (API 명세 v2 §3 {@code PUT /me/settings}).
 * <b>{@code null} 필드는 기존값을 유지한다</b> — 부분 수정을 허용한다.
 * 설명 프로필과 알림 스위치를 함께 다룬다(v1 {@code PUT /me/notifications} 흡수).
 */
@Schema(description = "설정 부분 수정. null 필드는 기존값을 유지한다")
public record SettingsUpdateRequest(
        @Schema(description = "주거래 은행 코드", example = "KB", nullable = true)
        String defaultBankCode,

        @Schema(description = "환전 우대율 (0.0~1.0)", example = "0.7",
                minimum = "0", maximum = "1", nullable = true)
        Double fxDiscountRatio,

        @Schema(description = "설명 선호 3단계", example = "standard",
                allowableValues = {"simple", "standard", "detailed"}, nullable = true)
        String explainLevel,

        @Schema(description = "익숙한 설명 분야", example = "finance",
                allowableValues = {"finance", "dev", "marketing", "plain"}, nullable = true)
        String explainDomain,

        @Schema(description = "회차 집행 예정 알림", example = "true", nullable = true)
        Boolean notifyStepDue,

        @Schema(description = "시장 국면 전환 알림", example = "true", nullable = true)
        Boolean notifyRegimeShift,

        @Schema(description = "마감 임박 알림", example = "true", nullable = true)
        Boolean notifyDeadlineNear,

        @Schema(description = "목표 구간 진입 알림", example = "false", nullable = true)
        Boolean notifyTargetZone,

        @Schema(description = "집중도 경고 알림", example = "true", nullable = true)
        Boolean notifyConcentration) {
}
