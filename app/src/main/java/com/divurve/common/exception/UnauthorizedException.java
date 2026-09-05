package com.divurve.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 인증 주체가 없는(로그인/데모 토큰이 없는) 요청이 보호 엔드포인트에 접근할 때 던진다.
 * {@code @ResponseStatus} 로 Spring 이 401 Unauthorized 로 매핑한다.
 *
 * <p>현재 대부분의 엔드포인트는 무인증이지만, {@code /me/**} 는 요청 주체 식별이 필수이므로
 * {@link com.divurve.api.config.auth.CurrentUserContext} 가 비어 있으면 이 예외로 401 을 낸다.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("인증이 필요합니다.");
    }
}
