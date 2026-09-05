package com.divurve.api.dto.ai;

import java.util.List;
import java.util.Map;

/**
 * 자연어 목표 파싱 응답 (POST /ai/parse-goal, 명세 4장).
 * AI 는 구조화만 한다 — 금액·확률·비용을 계산하지 않는다 (NFR-AI-01).
 * confidence 가 낮은 필드는 클라이언트에서 사용자 확인을 받고, 승인 전엔 저장하지 않는다.
 */
public record ParseGoalResponse(
        Parsed parsed,
        Map<String, Double> confidence,
        List<String> missing) {

    /** 구조화된 목표 제약. */
    public record Parsed(
            String kind,
            String purpose,
            String currencyCode,
            Double targetAmount,
            String recurInterval) {
    }
}
