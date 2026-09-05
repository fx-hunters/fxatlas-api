package com.divurve.api.dto.auth;

/** 로그인 요청 바디 (POST /auth/login). */
public record LoginRequest(String email, String password) {
}
