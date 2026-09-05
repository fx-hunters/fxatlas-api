package com.divurve.api.dto.auth;

/** 회원가입 요청 바디 (POST /auth/signup). */
public record SignupRequest(String email, String password, String name) {
}
