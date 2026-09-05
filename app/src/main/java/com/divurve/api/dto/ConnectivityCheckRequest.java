package com.divurve.api.dto;

/**
 * 테스트 행 생성 요청 바디. Jackson 전역 SNAKE_CASE 전략에 따라 {@code message} 로 역직렬화된다.
 */
public record ConnectivityCheckRequest(String message) {
}
