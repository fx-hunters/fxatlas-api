package com.divurve.api.dto.auth;

/** 토큰 갱신 요청 바디 (POST /auth/refresh). */
public record RefreshRequest(String refreshToken) {
}
