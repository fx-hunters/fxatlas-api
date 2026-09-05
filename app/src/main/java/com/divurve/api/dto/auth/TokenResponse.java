package com.divurve.api.dto.auth;

/**
 * 인증 토큰 응답 (signup·login·refresh·demo 공통).
 * 액세스 토큰 30분, 리프레시 토큰 14일 (명세 1.1). 데모 계정은 {@code isDemo=true}.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean isDemo) {
}
