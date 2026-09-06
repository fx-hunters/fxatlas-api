package com.divurve.api.config;

import com.divurve.common.exception.ApiException;
import com.divurve.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러 (명세 1.3). 모든 예외를 {@code {error:{code,message,field,detail}}} 엔벨로프로 변환해
 * 프론트가 상태코드와 무관하게 동일한 파싱 규칙으로 에러를 처리하게 한다.
 *
 * <p>{@link ApiException} 계열은 각자 지정한 상태코드·에러코드로, 그 외 예기치 못한 예외는 500 으로 매핑한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 도메인/웹 계층이 의도적으로 던진 API 예외 → 지정 상태코드 + 에러 엔벨로프. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getField(), ex.getDetail()));
    }

    /** 그 외 예기치 못한 예외 → 500. 내부 메시지는 노출하지 않고 로그로만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "예상치 못한 오류가 발생했습니다.", null, null));
    }
}
