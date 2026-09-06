package com.divurve.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 바디 (POST /auth/login).
 *
 * <p>둘 중 하나라도 없으면(공백만 있거나 JSON 값 자체가 없는 경우 포함) {@code @Valid} 가
 * {@code MethodArgumentNotValidException} 을 던져 400 {@code VALIDATION_FAILED} 로 응답한다 — 누락
 * 입력이 {@code AuthService.login} 의 {@code Objects.requireNonNull} 까지 내려가 500 으로 새지
 * 않는다(이슈 #75).
 */
public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.") String email,
        @NotBlank(message = "비밀번호는 필수입니다.") String password) {
}
