package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 Forbidden 응답 — <b>타인 리소스 접근</b>(NFR-SE-02) 한 가지에만 쓴다.
 *
 * <p>에러코드는 명세 §1.3 의 6종 닫힌 집합을 지키기 위해 {@code FORBIDDEN} 으로 고정한다.
 * v1 의 투기 목적 게이트({@code SPECULATIVE_PURPOSE_BLOCKED})는 명세 v2 §0.1 에서 삭제됐다 —
 * {@code is_speculative} 는 ERD 에만 있고 요구사항 v2 에 근거가 없어 §8 미결정으로 이동했다.
 */
public class ForbiddenException extends ApiException {

    private static final String CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, CODE, message, null);
    }

    /**
     * @param field 관련 요청 필드명
     */
    public ForbiddenException(String message, String field) {
        super(HttpStatus.FORBIDDEN, CODE, message, field);
    }
}
