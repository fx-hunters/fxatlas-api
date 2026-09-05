package com.divurve.engine.riskprofile;

/**
 * 성향 진단 산출 결과 (이슈 #10, FR-ON-02). 결정론적 계산의 출력이다 — LLM/외부 API 가 아니라
 * {@link RiskProfileScorer} 가 응답값으로부터 직접 만든다.
 *
 * @param score    문항 응답값 합계 (원점수)
 * @param riskType 등급 코드 — {@code stable}(안정) · {@code balanced}(균형) · {@code flexible}(유연)
 */
public record RiskAssessment(int score, String riskType) {
}
