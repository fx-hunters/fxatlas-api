package com.divurve.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 갱신 요청 바디 (POST /auth/refresh).
 *
 * <p>{@code refresh_token} 이 없으면(공백만 있거나 JSON 값 자체가 없는 경우 포함) {@code @Valid} 가
 * {@code MethodArgumentNotValidException} 을 던져 400 {@code VALIDATION_FAILED} 로 응답한다 — 누락
 * 입력이 {@code AuthService.refreshAccessToken} 의 {@code Objects.requireNonNull} 까지 내려가 500 으로
 * 새지 않는다(이슈 #75).
 */
public record RefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.") String refreshToken) {
}
