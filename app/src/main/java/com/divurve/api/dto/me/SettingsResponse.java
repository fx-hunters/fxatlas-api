package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 설정 응답 (API 명세 v2 §3 {@code GET/PUT /me/settings}).
 * 설명 프로필·주거래 은행·환전 우대율과 <b>알림 스위치 5종</b>을 한 응답에 담는다 —
 * v1 의 {@code PUT /me/notifications} 는 이 리소스로 흡수됐다.
 *
 * <p>필드명은 DB 컬럼명 그대로다 — {@code explain_level}·{@code explain_domain}·{@code notify_*}.
 * {@code base_spread_ratio}(은행 기본 스프레드)·{@code effective_spread_ratio}(우대율 적용 실효 스프레드)는
 * engine 계산 결과다(FR-MY-04).
 */
@Schema(description = "설명 프로필·거래 설정·알림 스위치")
public record SettingsResponse(
        @Schema(description = "주거래 은행 코드. 미지정이면 null", example = "KB", nullable = true)
        String defaultBankCode,

        @Schema(description = "환전 우대율 (0.0~1.0)", example = "0.7", minimum = "0", maximum = "1")
        double fxDiscountRatio,

        @Schema(description = "설명 선호 3단계 — 표현에만 쓰고 계산에 넣지 않는다(FR-MY-03)",
                example = "standard", allowableValues = {"simple", "standard", "detailed"})
        String explainLevel,

        @Schema(description = "익숙한 설명 분야 — 비유 선택에만 쓴다", example = "finance",
                allowableValues = {"finance", "dev", "marketing", "plain"})
        String explainDomain,

        @Schema(description = "은행 기본 스프레드 비율 (마스터 조회값)", example = "0.0175")
        double baseSpreadRatio,

        @Schema(description = "실효 스프레드 비율 = base × (1 − discount)", example = "0.00525")
        double effectiveSpreadRatio,

        @Schema(description = "회차 집행 예정 알림", example = "true")
        boolean notifyStepDue,

        @Schema(description = "시장 국면 전환 알림", example = "true")
        boolean notifyRegimeShift,

        @Schema(description = "마감 임박 알림", example = "true")
        boolean notifyDeadlineNear,

        @Schema(description = "목표 구간 진입 알림. 기본값만 false 다", example = "false")
        boolean notifyTargetZone,

        @Schema(description = "집중도 경고 알림", example = "true")
        boolean notifyConcentration) {
}
