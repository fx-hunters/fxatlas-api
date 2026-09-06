package com.divurve.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.domain.port.AiProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link MockAiProvider} 외부 AI API 시뮬레이션 테스트.
 * explain/parseGoal 응답이 고정 스키마를 따르는지 검증.
 */
class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();

    @Test
    void explain_concise_프로필이면_짧은_서술을_반환한다() {
        AiProvider.ExplainResult result = provider.explain("concise", Map.of("total_amount", 100000.0));

        assertThat(result.narrative()).isNotBlank();
        assertThat(result.narrative()).contains("자산");
    }

    @Test
    void explain_detailed_프로필이면_상세한_서술을_반환한다() {
        AiProvider.ExplainResult result = provider.explain("detailed", Map.of(
                "total_amount", 100000.0,
                "risk_score", 5.5));

        assertThat(result.narrative()).isNotBlank();
        assertThat(result.narrative()).contains("자산");
        assertThat(result.narrative()).contains("위험");
    }

    @Test
    void explain_metrics에_없는_필드는_무시한다() {
        AiProvider.ExplainResult result = provider.explain("concise", Map.of());

        assertThat(result.narrative()).isNotBlank();
    }

    @Test
    void explain_detailed_프로필이고_metrics가_비어_있으면_수치_문장을_생략한다() {
        AiProvider.ExplainResult result = provider.explain("detailed", Map.of());

        assertThat(result.narrative()).isNotBlank();
        // 엔진 수치가 없으면 AI 가 수치를 지어내지 않고 서술만 남긴다 (NFR-AI-01).
        assertThat(result.narrative()).doesNotContain("총 자산은");
        assertThat(result.narrative()).doesNotContain("위험 점수는");
        assertThat(result.narrative()).contains("포트폴리오");
    }

    @Test
    void explain_detailed_프로필이고_risk_score만_있으면_자산_문장을_생략한다() {
        AiProvider.ExplainResult result = provider.explain("detailed", Map.of("risk_score", 5.5));

        assertThat(result.narrative()).doesNotContain("총 자산은");
        assertThat(result.narrative()).contains("위험 점수는 5.5");
    }

    @Test
    void explain_detailed_프로필이고_total_amount만_있으면_위험_문장을_생략한다() {
        AiProvider.ExplainResult result =
                provider.explain("detailed", Map.of("total_amount", 100000.0));

        assertThat(result.narrative()).contains("총 자산은 100000.0");
        assertThat(result.narrative()).doesNotContain("위험 점수는");
    }

    @Test
    void explain_profile이_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> provider.explain(null, Map.of("amount", 100.0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void explain_metrics가_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> provider.explain("concise", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseGoal_고정_스키마_ParseResult를_반환한다() {
        AiProvider.ParseResult result = provider.parseGoal("월 1000달러 저축 목표");

        assertThat(result.kind()).isEqualTo("wealth");
        assertThat(result.purpose()).isEqualTo("retirement");
        assertThat(result.currencyCode()).isEqualTo("USD");
        assertThat(result.targetAmount()).isEqualTo(100000.0);
        assertThat(result.recurInterval()).isEqualTo("monthly");
    }

    @Test
    void parseGoal_confidence_map이_정의되어_있다() {
        AiProvider.ParseResult result = provider.parseGoal("자연어 목표");

        assertThat(result.confidence()).isNotEmpty();
        assertThat(result.confidence()).containsKeys("kind", "purpose", "currencyCode");
        assertThat(result.confidence().values())
                .allMatch(v -> v >= 0.0 && v <= 1.0);
    }

    @Test
    void parseGoal_missing_list가_정의되어_있다() {
        AiProvider.ParseResult result = provider.parseGoal("자연어 목표");

        assertThat(result.missing()).isNotNull();
        assertThat(result.missing()).isEmpty();
    }

    @Test
    void parseGoal_text가_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> provider.parseGoal(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseGoal_여러_번_호출해도_같은_구조를_반환한다() {
        AiProvider.ParseResult result1 = provider.parseGoal("목표 1");
        AiProvider.ParseResult result2 = provider.parseGoal("목표 2");

        assertThat(result1.kind()).isEqualTo(result2.kind());
        assertThat(result1.purpose()).isEqualTo(result2.purpose());
        assertThat(result1.currencyCode()).isEqualTo(result2.currencyCode());
    }
}
