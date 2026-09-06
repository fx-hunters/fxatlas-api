package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AcquisitionRange} — 정기형 확보 외화 범위 (플래너 명세 §10.2·§10.3).
 */
@DisplayName("AcquisitionRange")
class AcquisitionRangeTest {

    private static final AcquisitionRange RANGE = new AcquisitionRange(
            new BigDecimal("350.00"), new BigDecimal("370.00"), new BigDecimal("385.00"));

    @Test
    @DisplayName("하단·기준·상단을 그대로 담는다")
    void constructor_KeepsValues() {
        assertThat(RANGE.low()).isEqualByComparingTo("350.00");
        assertThat(RANGE.base()).isEqualByComparingTo("370.00");
        assertThat(RANGE.high()).isEqualByComparingTo("385.00");
    }

    @Test
    @DisplayName("누적 범위는 회차 수를 곱한 값이다")
    void accumulate_MultipliesByRoundCount() {
        AcquisitionRange accumulated = RANGE.accumulate(6);

        assertThat(accumulated.low()).isEqualByComparingTo("2100.00");
        assertThat(accumulated.base()).isEqualByComparingTo("2220.00");
        assertThat(accumulated.high()).isEqualByComparingTo("2310.00");
    }

    @Test
    @DisplayName("회차가 0이면 누적도 0이다")
    void accumulate_ZeroRounds_ReturnsZero() {
        AcquisitionRange accumulated = RANGE.accumulate(0);

        assertThat(accumulated.low()).isEqualByComparingTo("0");
        assertThat(accumulated.base()).isEqualByComparingTo("0");
        assertThat(accumulated.high()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("음수 회차는 거부한다")
    void accumulate_NegativeRounds_Throws() {
        assertThatThrownBy(() -> RANGE.accumulate(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("null 값은 거부한다")
    void constructor_Null_Throws() {
        BigDecimal amount = BigDecimal.TEN;
        assertThatThrownBy(() -> new AcquisitionRange(null, amount, amount))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AcquisitionRange(amount, null, amount))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AcquisitionRange(amount, amount, null))
                .isInstanceOf(NullPointerException.class);
    }
}
