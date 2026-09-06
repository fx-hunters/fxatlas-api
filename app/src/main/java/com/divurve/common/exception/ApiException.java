package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인/웹 계층이 던지는 모든 API 예외의 공통 베이스 (명세 1.3 에러 엔벨로프). 상태코드·에러코드·필드·상세를
 * 함께 실어, {@code GlobalExceptionHandler} 가 {@code {error:{code,message,field,detail}}} 형태로 일관되게 변환한다.
 *
 * @see com.divurve.common.response.ErrorResponse
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final transient Object detail;

    /**
     * @param status  HTTP 상태코드
     * @param code    기계 판독용 에러코드 (예: {@code VALIDATION_FAILED})
     * @param message 사람이 읽는 메시지
     * @param field   관련 요청 필드명 (없으면 {@code null})
     * @param detail  부가 상세(대안·한계값 등, 없으면 {@code null})
     */
    protected ApiException(HttpStatus status, String code, String message, String field, Object detail) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public Object getDetail() {
        return detail;
    }
}
