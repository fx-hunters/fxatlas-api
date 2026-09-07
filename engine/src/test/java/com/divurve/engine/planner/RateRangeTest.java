package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RateRange} 테스트 — 환율 범위의 불변조건 (명세 §7·§9.1·§21-6).
 */
@DisplayName("RateRange")
class RateRangeTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("low <= base <= high 인 범위를 받는다")
    void acceptsOrderedRange() {
        RateRange rates = new RateRange(bd("1330.60"), bd("1359.50"), bd("1389.02"));

        assertThat(rates.low()).isEqualByComparingTo("1330.60");
        assertThat(rates.base()).isEqualByComparingTo("1359.50");
        assertThat(rates.high()).isEqualByComparingTo("1389.02");
    }

    @Test
    @DisplayName("세 값이 모두 같아도 유효하다 — 범위가 좁혀진 경우")
    void acceptsDegenerateRange() {
        RateRange rates = new RateRange(bd("1359.50"), bd("1359.50"), bd("1359.50"));

        assertThat(rates.low()).isEqualByComparingTo(rates.high());
    }

    @Test
    @DisplayName("환율이 0 이면 거부한다")
    void rejectsZeroRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateRange(BigDecimal.ZERO, bd("1"), bd("2")))
                .withMessageContaining("환율은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("환율이 음수면 거부한다")
    void rejectsNegativeRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateRange(bd("-1"), bd("1"), bd("2")))
                .withMessageContaining("환율은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("low 가 base 보다 크면 거부한다")
    void rejectsLowAboveBase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateRange(bd("1400"), bd("1359"), bd("1389")))
                .withMessageContaining("low <= base <= high");
    }

    @Test
    @DisplayName("base 가 high 보다 크면 거부한다")
    void rejectsBaseAboveHigh() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateRange(bd("1330"), bd("1400"), bd("1389")))
                .withMessageContaining("low <= base <= high");
    }

    @Test
    @DisplayName("null 값은 각각 거부한다")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RateRange(null, bd("1"), bd("2")))
                .withMessage("low");
        assertThatNullPointerException()
                .isThrownBy(() -> new RateRange(bd("1"), null, bd("2")))
                .withMessage("base");
        assertThatNullPointerException()
                .isThrownBy(() -> new RateRange(bd("1"), bd("2"), null))
                .withMessage("high");
    }

    @Test
    @DisplayName("100엔 고시를 1엔 기준으로 정규화한 값을 담는다 (§21-6)")
    void holdsPerUnitNormalizedRates() {
        // JPY 는 100엔당 원화로 고시된다. 정규화는 QuoteUnitNormalizer 의 몫이고,
        // 이 record 는 정규화가 끝난 per-unit 값만 받는다는 것을 계약으로 남긴다.
        RateRange perHundredYen = new RateRange(bd("890.00"), bd("900.00"), bd("910.00"));
        BigDecimal quoteUnit = bd("100");

        RateRange perUnit = new RateRange(
                perHundredYen.low().divide(quoteUnit),
                perHundredYen.base().divide(quoteUnit),
                perHundredYen.high().divide(quoteUnit));

        assertThat(perUnit.base()).isEqualByComparingTo("9.00");
    }
}
