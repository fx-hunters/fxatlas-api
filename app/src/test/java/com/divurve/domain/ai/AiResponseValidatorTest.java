package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AiResponseValidator} 수치 대조 테스트.
 * AI 가 생성한 narrative 에서 엔진 metrics 과 일치하는 숫자를 찾는지 검증.
 */
class AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator();

    @Test
    void validateNarrative_정확한_숫자를_포함하면_true() {
        String narrative = "귀사의 자산은 100000입니다.";
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_백분율_형식의_숫자도_인식한다() {
        String narrative = "위험도는 45%입니다.";
        Map<String, Object> metrics = Map.of("risk_percent", 45.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_허용_오차_1_범위내면_true() {
        String narrative = "자산은 101000입니다.";
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_허용_오차_초과면_false() {
        String narrative = "자산은 102000입니다.";
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isFalse();
    }

    @Test
    void validateNarrative_숫자를_찾지_못하면_false() {
        String narrative = "자산이 있습니다.";
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isFalse();
    }

    @Test
    void validateNarrative_null_narrative이면_false() {
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative(null, metrics);

        assertThat(result).isFalse();
    }

    @Test
    void validateNarrative_blank_narrative이면_false() {
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        boolean result = validator.validateNarrative("   ", metrics);

        assertThat(result).isFalse();
    }

    @Test
    void validateNarrative_여러_숫자가_있으면_하나라도_일치하면_true() {
        String narrative = "총액은 100000이고 수익은 15000입니다.";
        Map<String, Object> metrics = Map.of("total", 100000.0, "profit", 15000.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_0값_처리() {
        String narrative = "손실은 0입니다.";
        Map<String, Object> metrics = Map.of("loss", 0.0);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_소수점_숫자() {
        String narrative = "환율은 1.2345입니다.";
        Map<String, Object> metrics = Map.of("rate", 1.2345);

        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }

    @Test
    void validateNarrative_String_metrics_값은_무시한다() {
        String narrative = "통화는 USD입니다.";
        Map<String, Object> metrics = Map.of("currency", "USD", "amount", 100000.0);

        // String 값은 무시하고, 숫자 값만 검증
        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isFalse(); // 100000을 찾지 못함
    }

    @Test
    void validateNarrative_쉼표가_있는_숫자() {
        String narrative = "자산은 1,000,000입니다.";
        Map<String, Object> metrics = Map.of("amount", 1000000.0);

        // 쉼표가 제거되고 1000000으로 인식됨
        boolean result = validator.validateNarrative(narrative, metrics);

        assertThat(result).isTrue();
    }
}
