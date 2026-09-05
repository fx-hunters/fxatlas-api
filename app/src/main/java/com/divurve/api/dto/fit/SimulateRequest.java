package com.divurve.api.dto.fit;

/**
 * 분산효과 시뮬레이션 요청 (POST /fit/simulate, 명세 3.8).
 * 특정 통화 비중을 {@code delta_share} 만큼 조정했을 때의 효과를 계산한다.
 */
public record SimulateRequest(
        String currencyCode,
        double deltaShare) {
}
