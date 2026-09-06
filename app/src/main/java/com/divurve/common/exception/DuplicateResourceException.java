package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 Conflict 응답 (명세 §1.3). 이미 존재하는 리소스를 다시 만들려 할 때 던진다 —
 * 예: {@code fx_deposits} 의 (user, currency, bank) 유니크 제약 위반.
 *
 * <p>DB 유니크 제약이 먼저 터진 경우에는 {@code GlobalExceptionHandler} 가
 * {@code DataIntegrityViolationException} 을 같은 코드({@code DUPLICATE_RESOURCE})로 매핑한다.
 */
public class DuplicateResourceException extends ApiException {

    private static final String CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, CODE, message, null);
    }

    /**
     * @param field 중복을 일으킨 요청 필드명
     */
    public DuplicateResourceException(String message, String field) {
        super(HttpStatus.CONFLICT, CODE, message, field);
    }
}
