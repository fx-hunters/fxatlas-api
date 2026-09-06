package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AiResponseValidator} 수치 대조 테스트 (FR-AI-05, NFR-AI-02, 리뷰 B H3 대응).
 * "서술의 숫자가 facts 에 있는지"를 검사한다 — facts 에 없는 숫자(날조)가 있으면 실패해야 한다.
 */
class AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator();

    @Test
    void verify_서술의_숫자가_facts에_있으면_true() {
        boolean result = validator.verify(
                List.of("자산은 100000입니다."), Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_facts에_없는_숫자를_지어내면_false() {
        // 리뷰 B H3 — v1 은 이 케이스를 잡지 못했다(대조 방향이 반대였다).
        boolean result = validator.verify(
                List.of("자산은 999999입니다."), Map.of("amount", 100000.0));

        assertThat(result).isFalse();
    }

    @Test
    void verify_대조할_숫자가_없어도_facts가_비어있지_않으면_true() {
        // facts 에 숫자가 있더라도 서술이 숫자를 언급하지 않으면 날조 위험이 없다.
        boolean result = validator.verify(
                List.of("현재 상태는 안정적입니다."), Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_퍼센트_표기를_비율로_정규화해_대조한다() {
        // 리뷰 B M5 — facts 의 0.72 를 서술이 72% 로 써도 같은 값으로 인정한다.
        boolean result = validator.verify(
                List.of("변동성은 72%입니다."), Map.of("vol_percentile_5y", 0.72));

        assertThat(result).isTrue();
    }

    @Test
    void verify_허용_오차_범위_내면_true() {
        boolean result = validator.verify(
                List.of("자산은 100500입니다."), Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_허용_오차를_벗어나면_false() {
        boolean result = validator.verify(
                List.of("자산은 105000입니다."), Map.of("amount", 100000.0));

        assertThat(result).isFalse();
    }

    @Test
    void verify_중첩된_맵의_수치도_대조_대상에_포함한다() {
        boolean result = validator.verify(
                List.of("구간은 1346.0원에서 1431.0원 사이입니다."),
                Map.of("interval_80", Map.of("lo", 1346.0, "hi", 1431.0)));

        assertThat(result).isTrue();
    }

    @Test
    void verify_한자리_정수는_사소한_개수_표현으로_보고_대조하지_않는다() {
        boolean result = validator.verify(
                List.of("이 설명은 4개의 문장으로 구성됩니다."), Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_소수점이_있으면_한자리여도_대조한다() {
        boolean result = validator.verify(
                List.of("환율은 9.9입니다."), Map.of("rate", 1.2345));

        assertThat(result).isFalse();
    }

    @Test
    void verify_쉼표가_있는_숫자도_인식한다() {
        boolean result = validator.verify(
                List.of("자산은 1,000,000입니다."), Map.of("amount", 1000000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_sentences가_비어있으면_true() {
        boolean result = validator.verify(List.of(), Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_facts가_비어있고_서술에_큰_숫자가_있으면_false() {
        boolean result = validator.verify(
                List.of("자산은 100000입니다."), Map.of());

        assertThat(result).isFalse();
    }

    @Test
    void verify_0값과_정확히_일치하면_true() {
        boolean result = validator.verify(
                List.of("손실은 0.0입니다."), Map.of("loss", 0.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_sentences가_null이면_true() {
        boolean result = validator.verify(null, Map.of("amount", 100000.0));

        assertThat(result).isTrue();
    }

    @Test
    void verify_리스트에_담긴_숫자도_대조_대상에_포함한다() {
        boolean result = validator.verify(
                List.of("환율은 1346.0원입니다."), Map.of("rates", List.of(1346.0, 1431.0)));

        assertThat(result).isTrue();
    }

    @Test
    void verify_문자열_값은_대조_대상에서_제외된다() {
        boolean result = validator.verify(
                List.of("통화는 USD입니다."), Map.of("currency_code", "USD"));

        assertThat(result).isTrue();
    }
}
