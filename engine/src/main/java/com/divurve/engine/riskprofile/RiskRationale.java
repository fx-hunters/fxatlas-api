package com.divurve.engine.riskprofile;

/**
 * 간편 진단 문항별 판정 근거 (API 명세 v2 §5.1 {@code simple.rationale}, FR-DG-07).
 * `왜 이렇게 나왔나요?` 아코디언이 그대로 그리는 원본 데이터다.
 *
 * @param question 문항 코드 ({@code q1}·{@code q2}·{@code q3})
 * @param choice   선택지 코드 ({@code A}~{@code D})
 * @param points   해당 선택지의 점수 (A=0·B=1·C=2·D=3)
 * @param reading  선택 내용을 사용자 언어로 옮긴 한 문장
 */
public record RiskRationale(String question, String choice, int points, String reading) {
}
