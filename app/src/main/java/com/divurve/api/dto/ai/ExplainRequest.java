package com.divurve.api.dto.ai;

import java.util.Map;

/**
 * 엔진 결과 서술 요청 (POST /ai/explain, 명세 4장).
 * 엔진이 계산한 수치를 그대로 담아 전달하며, AI 는 이를 설명 프로필에 맞춰 서술만 한다 (NFR-AI-02).
 */
public record ExplainRequest(
        String profile,
        Map<String, Object> metrics) {
}
