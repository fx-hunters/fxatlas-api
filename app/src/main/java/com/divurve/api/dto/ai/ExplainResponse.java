package com.divurve.api.dto.ai;

/**
 * 엔진 결과 서술 응답 (POST /ai/explain, 명세 4장).
 * 입력으로 받은 엔진 수치를 그대로 인용해야 하며, 숫자 불일치가 감지되면 폐기된다 (NFR-AI-02).
 */
public record ExplainResponse(String narrative) {
}
