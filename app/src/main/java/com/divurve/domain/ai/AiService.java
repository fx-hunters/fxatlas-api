package com.divurve.domain.ai;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.port.AiProvider;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 기반 자연어 처리 유스케이스 (이슈 #23, NFR-AI-01~03).
 * 1. explain: 엔진 결과 조회 → AI 서술 요청 → 수치 대조 → 표현 필터 → 응답
 * 2. parseGoal: 자연어 입력 → AI 파싱 → confidence 반환 (저장하지 않음)
 *
 * AI 는 산술을 하지 않으며, 엔진이 계산한 수치를 그대로 인용해야 한다 (NFR-AI-01).
 * 수치 불일치 시 폐기·재생성한다 (NFR-AI-02).
 * 단정적·권유 표현은 후처리로 차단한다 (NFR-AI-03).
 */
@UseCase
public class AiService {

    private final AiProvider aiProvider;
    private final AiResponseValidator validator;
    private final NarrativeFilter narrativeFilter;
    private static final int MAX_RETRIES = 3;

    public AiService(
            AiProvider aiProvider,
            AiResponseValidator validator,
            NarrativeFilter narrativeFilter) {
        this.aiProvider = Objects.requireNonNull(aiProvider);
        this.validator = Objects.requireNonNull(validator);
        this.narrativeFilter = Objects.requireNonNull(narrativeFilter);
    }

    /**
     * 엔진 결과를 설명 프로필에 맞춰 서술한다.
     * 재시도 로직: 수치 불일치 시 최대 3회 재생성.
     *
     * @param profile 설명 유형 (간결/상세 등)
     * @param metrics 엔진이 계산한 수치
     * @return 필터링된 서술 (또는 null if 최대 재시도 초과)
     */
    @Transactional(readOnly = true)
    public String explain(String profile, Map<String, Object> metrics) {
        Objects.requireNonNull(profile, "profile 은 null 이 아니어야 합니다");
        Objects.requireNonNull(metrics, "metrics 는 null 이 아니어야 합니다");

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            AiProvider.ExplainResult result = aiProvider.explain(profile, metrics);

            if (validator.validateNarrative(result.narrative(), metrics)) {
                String filtered = narrativeFilter.filter(result.narrative());
                return filtered;
            }
            // 수치 불일치면 재시도
        }

        // 최대 재시도 초과
        return null;
    }

    /**
     * 자연어 목표를 구조화된 제약으로 파싱한다.
     * 저장하지 않고, confidence 점수와 함께 반환하여
     * 클라이언트가 사용자 확인을 받도록 한다.
     *
     * @param text 사용자의 자연어 목표
     * @return 구조화 결과 (Parsed + confidence + missing)
     */
    @Transactional(readOnly = true)
    public ParsedGoal parseGoal(String text) {
        Objects.requireNonNull(text, "text 는 null 이 아니어야 합니다");

        AiProvider.ParseResult result = aiProvider.parseGoal(text);

        return new ParsedGoal(
                result.kind(),
                result.purpose(),
                result.currencyCode(),
                result.targetAmount(),
                result.recurInterval(),
                result.confidence(),
                result.missing());
    }

    /**
     * parseGoal 반환 DTO.
     */
    public record ParsedGoal(
            String kind,
            String purpose,
            String currencyCode,
            Double targetAmount,
            String recurInterval,
            Map<String, Double> confidence,
            java.util.List<String> missing) {}
}
