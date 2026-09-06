package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link NarrativeFilter} 금지 표현 탐지 테스트 (FR-AI-07, NFR-RG-01).
 * v1 은 {@code ***} 마스킹이라 훼손 문장이 그대로 나갔다(리뷰 B M1) — 이제는 탐지만 하고,
 * {@link AiService} 가 발견 시 응답 전체를 폴백으로 바꾼다.
 */
class NarrativeFilterTest {

    private final NarrativeFilter filter = new NarrativeFilter();

    @Test
    void detect_단정적_표현_확실히를_찾는다() {
        assertThat(filter.detect("주식은 확실히 오를 것입니다.")).contains("확실히");
    }

    @Test
    void detect_단정적_표현_반드시를_찾는다() {
        assertThat(filter.detect("달러는 반드시 강세를 보일 것입니다.")).contains("반드시");
    }

    @Test
    void detect_단정적_표현_틀림없이를_찾는다() {
        assertThat(filter.detect("이 투자는 틀림없이 성공할 것입니다.")).contains("틀림없이");
    }

    @Test
    void detect_완결된_매수_지시_표현을_찾는다() {
        assertThat(filter.detect("이 자산을 매수하세요.")).contains("매수하세요");
    }

    @Test
    void detect_수익_보장_표현을_찾는다() {
        assertThat(filter.detect("이 상품은 수익을 보장합니다.")).isNotEmpty();
    }

    @Test
    void detect_안전한_표현은_찾지_않는다() {
        assertThat(filter.detect("포트폴리오의 분산 정도가 중간 수준입니다.")).isEmpty();
    }

    @Test
    void detect_투자하지_않는다는_오탐하지_않는다() {
        // 리뷰 B M1 — v1 의 "투자하" 단일 형태소 매칭은 부정문까지 잡았다.
        assertThat(filter.detect("이 자산에는 투자하지 않는 것을 권합니다.")).isEmpty();
    }

    @Test
    void detect_추천이라는_단어_자체는_오탐하지_않는다() {
        // 리뷰 B M1 — "추천" 단일 단어는 더 이상 패턴이 아니다. "매수를 추천" 처럼 완결된 형태만 잡는다.
        assertThat(filter.detect("여러 통화에 나눠 담는 분산이 추천되는 방식입니다.")).isEmpty();
    }

    @Test
    void detect_null_텍스트는_빈_목록을_반환한다() {
        assertThat(filter.detect(null)).isEmpty();
    }

    @Test
    void detect_blank_텍스트는_빈_목록을_반환한다() {
        assertThat(filter.detect("   ")).isEmpty();
    }

    @Test
    void detect_여러_금지_표현을_모두_찾되_중복은_제거한다() {
        assertThat(filter.detect("반드시 매수하세요. 반드시 그렇습니다."))
                .containsExactly("반드시", "매수하세요");
    }
}
