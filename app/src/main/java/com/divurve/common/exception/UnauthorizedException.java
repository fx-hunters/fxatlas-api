package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증 주체가 없는(로그인/데모 토큰이 없는) 요청이 보호 엔드포인트에 접근할 때 던진다.
 * 전역 예외 핸들러가 401 Unauthorized + 에러 엔벨로프로 매핑한다.
 *
 * <p>현재 대부분의 엔드포인트는 무인증이지만, {@code /me/**} 는 요청 주체 식별이 필수이므로
 * {@link com.divurve.api.config.auth.CurrentUserContext} 가 비어 있으면 이 예외로 401 을 낸다.
 */
public class UnauthorizedException extends ApiException {

    private static final String CODE = "UNAUTHORIZED";

    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, CODE, "인증이 필요합니다.", null);
    }

    /**
     * @param message 사람이 읽는 메시지. 로그인 실패처럼 "없는 계정"과 "틀린 비밀번호"를 구분해 주면
     *                안 되는 상황에서는 <b>양쪽에 완전히 같은 문자열</b>을 넘겨야 한다(사용자 열거 방지).
     */
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, CODE, message, null);
    }
}
