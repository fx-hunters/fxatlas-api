package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 입력값 형식·범위 오류 (명세 §1.3, 400 {@code VALIDATION_FAILED}). 전역 예외 핸들러가
 * 400 Bad Request + 에러 엔벨로프로 매핑한다.
 *
 * <p>engine 순수 함수가 던지는 {@link IllegalArgumentException} 을 도메인 서비스가 받아 이 예외로 변환해,
 * 계산 계약 위반을 사용자 입력 오류(400)로 표면화한다.
 *
 * <p>에러코드는 명세 §1.3 의 6종 닫힌 집합을 지키기 위해 {@code VALIDATION_FAILED} 로 고정한다.
 * 어떤 입력이 문제였는지는 {@code field} 로 알린다 — v1 의 도메인별 상세 코드
 * ({@code SAFE_RATIO_BELOW_FLOOR} 등 Route 계산 의존 4종)는 명세 v2 §0.1 에서 삭제됐다.
 */
public class InvalidRequestException extends ApiException {

    private static final String CODE = "VALIDATION_FAILED";

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, CODE, message, null);
    }

    /**
     * @param field 문제가 된 요청 필드명 (예: {@code quantity})
     */
    public InvalidRequestException(String message, String field) {
        super(HttpStatus.BAD_REQUEST, CODE, message, field);
    }
}
