package com.divurve.domain.port;

import java.util.Map;

/**
 * 생성형 AI 포트 (명세 4장, NFR-AI-01~03).
 * domain 은 이 인터페이스만 알며, 실제 구현(infra 의 ClaudeAiProvider 등)은 알지 못한다(DIP).
 * AI 는 산술을 하지 않으며, 고정 스키마만 반환한다.
 */
public interface AiProvider {

    /**
     * 엔진 결과를 설명 프로필에 맞춰 서술한다.
     * @param profile 설명 유형 (간결/상세 등)
     * @param metrics 엔진이 계산한 수치 (금액·확률 등)
     * @return 서술 결과 (narrative)
     */
    ExplainResult explain(String profile, Map<String, Object> metrics);

    /**
     * 자연어 목표를 구조화된 제약으로 파싱한다.
     * @param text 사용자의 자연어 목표
     * @return 구조화 결과 (Parsed + confidence + missing)
     */
    ParseResult parseGoal(String text);

    /**
     * explain 결과 스키마.
     * @param narrative 서술 문장 (단정적 표현·투자 권유 차단 전 원문)
     */
    record ExplainResult(String narrative) {}

    /**
     * parseGoal 결과 스키마.
     * @param kind 목표 유형 (wealth/income 등)
     * @param purpose 목적 (education/retirement 등)
     * @param currencyCode 화폐 코드 (USD/EUR 등)
     * @param targetAmount 목표 금액 (수치가 아니면 null)
     * @param recurInterval 재정투입 주기 (monthly/yearly 등)
     * @param confidence 각 필드의 신뢰도 0~1 (낮으면 사용자 재확인 권장)
     * @param missing 구조화되지 못한 필드 목록
     */
    record ParseResult(
            String kind,
            String purpose,
            String currencyCode,
            Double targetAmount,
            String recurInterval,
            Map<String, Double> confidence,
            java.util.List<String> missing) {}
}
