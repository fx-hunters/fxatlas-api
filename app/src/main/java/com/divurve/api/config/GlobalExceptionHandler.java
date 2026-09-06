package com.divurve.api.config;

import com.divurve.common.exception.ApiException;
import com.divurve.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러 (명세 §1.3). 모든 예외를 {@code {error:{code,message,field}}} 엔벨로프로 변환해
 * 프론트가 상태코드와 무관하게 동일한 파싱 규칙으로 에러를 처리하게 한다.
 *
 * <p>에러코드는 명세 §1.3 의 6종만 낸다 — {@code VALIDATION_FAILED}(400) · {@code UNAUTHORIZED}(401) ·
 * {@code FORBIDDEN}(403) · {@code NOT_FOUND}(404) · {@code DUPLICATE_RESOURCE}(409) ·
 * {@code NOT_IMPLEMENTED}(501). {@link ApiException} 계열이 각자 코드를 고정하고, Spring 이 요청 처리
 * 과정에서 직접 던지는 예외들은 아래 핸들러가 이 6종으로 접어 넣는다.
 *
 * <p>예기치 못한 예외(500)는 명세에 없는 상황이므로 {@code INTERNAL_ERROR} 고정 코드·고정 문구로 낸다.
 * <b>원문 메시지는 응답에 싣지 않는다</b> — 외부 연동 실패 메시지에는 엔드포인트 URL·API 키가 들어갈 수 있다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";

    /** 도메인/웹 계층이 의도적으로 던진 API 예외 → 지정 상태코드 + 에러 엔벨로프. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getField()));
    }

    /**
     * 요청 본문 파싱 실패(잘못된 JSON·타입 불일치) → 400. Jackson 원문 메시지에는 클래스명·필드 경로가
     * 들어가므로 그대로 노출하지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("요청 본문 파싱 실패", ex);
        return badRequest("요청 본문을 해석할 수 없습니다.", null);
    }

    /** 필수 쿼리 파라미터 누락 → 400. 누락된 파라미터명을 {@code field} 로 알린다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("필수 요청 파라미터가 없습니다.", ex.getParameterName());
    }

    /** 파라미터 타입 변환 실패(예: 경로 변수의 잘못된 UUID·숫자) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("요청 파라미터 형식이 올바르지 않습니다.", ex.getName());
    }

    /** {@code @Valid} 검증 실패 → 400. 첫 위반 필드와 메시지를 그대로 전달한다(작성자가 쓴 문구). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> badRequest(error.getDefaultMessage(), error.getField()))
                .orElseGet(() -> badRequest("요청 값이 올바르지 않습니다.", null));
    }

    /**
     * 계약 위반으로 서비스/엔진이 던진 {@link IllegalArgumentException} → 400.
     *
     * <p>도메인 서비스는 원칙적으로 {@code InvalidRequestException} 으로 변환해서 던지지만,
     * 변환이 누락된 경로(예: 경로 변수 {@code UUID.fromString})가 500 으로 새지 않게 하는 안전망이다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("잘못된 인자", ex);
        return badRequest("요청 값이 올바르지 않습니다.", null);
    }

    /** DB 제약 위반(유니크 등) → 409. SQL 원문·제약명은 노출하지 않는다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("데이터 무결성 제약 위반", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(DUPLICATE_RESOURCE, "이미 존재하는 리소스입니다.", null));
    }

    /**
     * 존재하지 않는 경로, 또는 지원하지 않는 HTTP 메서드 → 404 {@code NOT_FOUND}.
     *
     * <p>명세 §1.3 의 코드 집합은 6종으로 닫혀 있어 405 에 대응하는 코드가 없다. 405 로 내리면
     * 프론트가 매핑할 코드가 없으므로, "그 경로+메서드 조합의 리소스는 없다"는 의미로 404 하나로 모은다.
     */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleUnknownEndpoint(Exception ex) {
        log.debug("알 수 없는 엔드포인트", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", null));
    }

    /** 그 외 예기치 못한 예외 → 500. 내부 메시지는 노출하지 않고 로그로만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "예상치 못한 오류가 발생했습니다.", null));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, String field) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_FAILED, message, field));
    }
}
