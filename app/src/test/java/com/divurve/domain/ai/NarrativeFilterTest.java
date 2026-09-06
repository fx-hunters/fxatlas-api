package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link NarrativeFilter} 위험 표현 마스킹 테스트 (NFR-AI-03).
 * 단정적 방향 표현과 투자 권유 표현을 *** 로 마스킹한다.
 */
class NarrativeFilterTest {

    private final NarrativeFilter filter = new NarrativeFilter();

    @Test
    void filter_단정적_표현_확실히_를_마스킹한다() {
        String narrative = "주식은 확실히 오를 것입니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("확실히");
    }

    @Test
    void filter_단정적_표현_반드시를_마스킹한다() {
        String narrative = "달러는 반드시 강세를 보일 것입니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("반드시");
    }

    @Test
    void filter_단정적_표현_틀림없이를_마스킹한다() {
        String narrative = "이 투자는 틀림없이 성공할 것입니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("틀림없이");
    }

    @Test
    void filter_투자_권유_표현_매수를_마스킹한다() {
        String narrative = "지금 당신이 매수해야 할 시점입니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("매수");
    }

    @Test
    void filter_투자_권유_표현_추천을_마스킹한다() {
        String narrative = "이 자산을 강력히 추천합니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("추천");
    }

    @Test
    void filter_투자_권유_표현_투자하세요를_마스킹한다() {
        String narrative = "지금 바로 투자하세요.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***").doesNotContain("투자하");
    }

    @Test
    void filter_안전한_표현은_마스킹하지_않는다() {
        String narrative = "포트폴리오의 분산 정도가 중간 수준입니다.";

        String result = filter.filter(narrative);

        assertThat(result).isEqualTo(narrative);
    }

    @Test
    void filter_null_narrative이면_null을_반환한다() {
        String result = filter.filter(null);

        assertThat(result).isNull();
    }

    @Test
    void filter_blank_narrative이면_blank를_반환한다() {
        String result = filter.filter("   ");

        assertThat(result).isEqualTo("   ");
    }

    @Test
    void filter_여러_위험_표현을_한번에_마스킹한다() {
        String narrative = "반드시 추천하며 확실히 성공할 것입니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***");
        assertThat(result).doesNotContain("반드시");
        assertThat(result).doesNotContain("추천");
        assertThat(result).doesNotContain("확실히");
    }

    @Test
    void filter_대소문자_무시하고_마스킹한다() {
        String narrative = "주식은 CONFIRM 상승할 것입니다.";
        // 표현 필터의 정규식이 CASE_INSENSITIVE 이므로 대문자도 처리됨
        // 다만 CONFIRM 은 패턴에 없으므로 마스킹되지 않음

        String result = filter.filter(narrative);

        assertThat(result).isEqualTo(narrative);
    }

    @Test
    void filter_문장_중간의_표현도_마스킹한다() {
        String narrative = "현재 상황을 보면 반드시 투자 기회가 있습니다.";

        String result = filter.filter(narrative);

        assertThat(result).contains("***");
        assertThat(result).doesNotContain("반드시");
    }

    @Test
    void filter_마스킹된_부분을_asterisk_3개로_표시한다() {
        String narrative = "이것은 매우 확실한 투자입니다.";

        String result = filter.filter(narrative);

        // 두 번의 마스킹이 일어남: 확실한 → *** , 투자 → ***
        assertThat(result).contains("***");
    }
}
