package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 Forbidden 응답. 투기/레버리지 목적 등으로 계획 생성이 금지된 경우 던진다 (FR-RT-22).
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message, null, null);
    }

    public ForbiddenException(String code, String message, String field) {
        super(HttpStatus.FORBIDDEN, code, message, field, null);
    }

    public ForbiddenException(String code, String message, Object detail) {
        super(HttpStatus.FORBIDDEN, code, message, null, detail);
    }
}
