package com.divurve.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증 토큰 응답 (signup·login·refresh·demo 공통, API 명세 v2 §3).
 * 액세스 토큰 30분, 리프레시 토큰 14일. 데모 계정은 {@code is_demo=true}.
 *
 * <p>{@code onboarded} 는 초기 설정 완료 여부다 — false 면 클라이언트가 초기 설정 화면으로 보낸다
 * (FR-IS-01·FR-IS-07). 신규 가입은 항상 false, 샘플 계정은 샘플 데이터가 이미 채워져 있으므로 true 다.
 */
@Schema(description = "인증 토큰과 초기 설정 완료 여부")
public record TokenResponse(
        @Schema(description = "액세스 토큰 (30분)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "리프레시 토큰 (14일)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "액세스 토큰 만료까지 남은 초", example = "1800")
        long expiresIn,

        @Schema(description = "샘플(데모) 계정 여부", example = "false")
        boolean isDemo,

        @Schema(description = "초기 설정 완료 여부. false 면 초기 설정으로 보낸다", example = "false")
        boolean onboarded) {
}
