package com.divurve.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 요청한 리소스가 존재하지 않을 때 던진다. {@code @ResponseStatus} 로 Spring 이 404 Not Found 로 매핑한다.
 *
 * <p>예: 아직 성향 진단을 하지 않은 사용자가 {@code GET /me/risk-profile} 를 호출한 경우.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
