package com.divurve.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원가입 요청 바디 (POST /auth/signup, 이슈 #22).
 *
 * <p>온보딩 목적 선택은 요구사항 v2에서 제거됐다 — "오늘 무엇을 하시겠어요?" 목적 선택으로
 * 앱/홈을 분기하지 않는다(요구사항 v2 §1). API 명세 v2 §5에도 대응 필드가 없다(이슈 #59).
 *
 * <p>세 필드 모두 공백만 있거나 없으면(JSON 값 자체가 없는 경우 포함) {@code @Valid} 가
 * {@code MethodArgumentNotValidException} 을 던지고, 전역 예외 핸들러가 400 {@code VALIDATION_FAILED}
 * 로 변환한다 — 누락 입력이 서비스 계층까지 내려가 500 으로 새지 않는다.
 */
public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.") String email,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        @NotBlank(message = "이름은 필수입니다.") String name) {
}
