package com.divurve.engine.weight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link QuoteUnitNormalizer} — 고시 단위 정규화 (ERD §4.1 {@code quote_unit}, 명세 §1.4). */
class QuoteUnitNormalizerTest {

    private final QuoteUnitNormalizer normalizer = new QuoteUnitNormalizer();

    @Test
    @DisplayName("명세 §4 fixture — JPY 는 원/100엔 고시라 1엔 기준으로 접는다 (939.13 → 9.3913)")
    void fixture_JPY는_100으로_나눈다() {
        assertThat(normalizer.toPerUnitRate("JPY", new BigDecimal("939.13")))
                .isEqualByComparingTo(new BigDecimal("9.3913"));
    }

    @Test
    @DisplayName("USD·EUR 는 1단위 고시라 그대로 둔다")
    void 단위통화는_그대로_둔다() {
        assertThat(normalizer.toPerUnitRate("USD", new BigDecimal("1382.40")))
                .isEqualByComparingTo(new BigDecimal("1382.40"));
        assertThat(normalizer.toPerUnitRate("EUR", new BigDecimal("1499.90")))
                .isEqualByComparingTo(new BigDecimal("1499.90"));
    }

    @Test
    @DisplayName("통화코드는 대소문자를 가리지 않는다")
    void 통화코드는_대소문자_무관이다() {
        assertThat(normalizer.isQuotedPerHundred("jpy")).isTrue();
        assertThat(normalizer.isQuotedPerHundred("usd")).isFalse();
        assertThat(normalizer.toPerUnitRate("jpy", new BigDecimal("900")))
                .isEqualByComparingTo(new BigDecimal("9"));
    }

    @Test
    @DisplayName("null 입력은 예외")
    void null_입력은_예외다() {
        assertThatThrownBy(() -> normalizer.toPerUnitRate(null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("통화코드");
        assertThatThrownBy(() -> normalizer.toPerUnitRate("USD", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("고시 환율");
        assertThatThrownBy(() -> normalizer.isQuotedPerHundred(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("통화코드");
    }
}
