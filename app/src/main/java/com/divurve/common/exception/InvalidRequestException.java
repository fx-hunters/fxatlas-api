package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청 본문이 유효하지 않을 때 던진다(예: 성향 진단 선택값 범위 초과, 환전 우대율이 0~1 범위 밖, 표시 모드 허용값 위반).
 * 전역 예외 핸들러가 400 Bad Request + 에러 엔벨로프로 매핑한다.
 *
 * <p>engine 순수 함수가 던지는 {@link IllegalArgumentException} 을 도메인 서비스가 받아 이 예외로 변환해,
 * 계산 계약 위반을 사용자 입력 오류(400)로 표면화한다. 도메인별 상세 에러코드(예: {@code SAFE_RATIO_BELOW_FLOOR})가
 * 필요하면 {@link #InvalidRequestException(String, String, String, Object)} 를 쓴다.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        this("VALIDATION_FAILED", message, null, null);
    }

    public InvalidRequestException(String code, String message, String field, Object detail) {
        super(HttpStatus.BAD_REQUEST, code, message, field, detail);
    }
}
