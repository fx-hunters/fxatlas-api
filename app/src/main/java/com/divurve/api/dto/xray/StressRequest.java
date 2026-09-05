package com.divurve.api.dto.xray;

import java.util.Map;

/**
 * 스트레스 시나리오 적용 요청 (POST /xray/stress).
 * 통화별 환율 변동 시나리오(비율)를 담는다.
 */
public record StressRequest(Map<String, Double> shocks) {
}
