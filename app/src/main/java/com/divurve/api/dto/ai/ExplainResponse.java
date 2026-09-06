package com.divurve.api.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 엔진 결과 서술 응답 (POST /ai/explain, API 명세 v2 §5.12).
 * 검증 실패 시에도 200 을 유지하며 {@code explanation.fallback=true} 와 고정 템플릿을 담는다
 * (FR-AI-06, NFR-AI-03).
 */
public record ExplainResponse(Explanation explanation, Verification verification) {

    /**
     * 서술 결과.
     *
     * @param sentences     서술 문장. {@code surface=forecast_summary} 는 항상 4개(FR-AI-04)
     * @param sentenceCount {@code sentences} 길이
     * @param explainLevel  반영된 설명 선호 ({@code user_settings} 에서 읽음, 요청 값이 아니다)
     * @param explainDomain 반영된 익숙한 설명 분야
     * @param fallback      검증 실패로 고정 템플릿을 냈는지
     */
    public record Explanation(
            List<String> sentences,
            @Schema(example = "4") int sentenceCount,
            @Schema(example = "standard") String explainLevel,
            @Schema(example = "dev") String explainDomain,
            boolean fallback) {
    }

    /**
     * 검증 결과 (§5 3·4단계).
     *
     * @param numericMatch   서술의 모든 숫자가 요청 {@code facts} 로 설명되는지
     * @param blockedPhrases 발견된 금지 표현. 없으면 빈 목록
     */
    public record Verification(boolean numericMatch, List<String> blockedPhrases) {
    }
}
