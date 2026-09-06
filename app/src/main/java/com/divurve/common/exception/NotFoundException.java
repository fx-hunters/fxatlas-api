package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청한 리소스가 존재하지 않을 때 던진다. 전역 예외 핸들러가 404 Not Found + 에러 엔벨로프로 매핑한다.
 *
 * <p>예: 아직 성향 진단을 하지 않은 사용자가 {@code GET /me/risk-profile} 를 호출한 경우.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message, null, null);
    }
}
