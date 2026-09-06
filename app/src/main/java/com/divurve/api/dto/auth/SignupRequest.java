package com.divurve.api.dto.auth;

/**
 * 회원가입 요청 바디 (POST /auth/signup, 이슈 #22).
 * onboardingPurpose: OVERSEAS_INVESTMENT 또는 FOREIGN_CURRENCY_GOAL
 */
public record SignupRequest(
        String email,
        String password,
        String name,
        String onboardingPurpose) {
}
