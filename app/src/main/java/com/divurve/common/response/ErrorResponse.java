package com.divurve.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 전역 에러 응답 엔벨로프 (명세 1.3). 모든 예외는 {@code {error:{code,message,field,detail}}} 형태로 변환된다.
 * {@code field}·{@code detail} 은 해당될 때만 실린다(비어 있으면 직렬화에서 생략).
 */
public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message, String field, Object detail) {
        return new ErrorResponse(new ErrorBody(code, message, field, detail));
    }

    /**
     * 에러 본문.
     *
     * @param code    기계 판독용 에러코드 (예: {@code VALIDATION_FAILED})
     * @param message 사람이 읽는 메시지
     * @param field   관련 요청 필드명 (없으면 생략)
     * @param detail  부가 상세 (없으면 생략)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, String field, Object detail) {
    }
}
