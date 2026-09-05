package com.divurve.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 요청 본문이 유효하지 않을 때 던진다(예: 성향 진단 선택값 범위 초과, 환전 우대율이 0~1 범위 밖).
 * {@code @ResponseStatus} 로 Spring 이 400 Bad Request 로 매핑한다.
 *
 * <p>engine 순수 함수가 던지는 {@link IllegalArgumentException} 을 도메인 서비스가 받아 이 예외로 변환해,
 * 계산 계약 위반을 사용자 입력 오류(400)로 표면화한다.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
