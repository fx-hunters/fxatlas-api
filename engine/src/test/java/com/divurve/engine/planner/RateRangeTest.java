package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RateRange} — 계산에 쓰는 환율 범위 (플래너 명세 §7·§9.1).
 */
@DisplayName("RateRange")
class RateRangeTest {

    @Test
    @DisplayName("하단·기준·상단을 그대로 담는다")
    void constructor_ValidRange_KeepsValues() {
        RateRange range = new RateRange(
                new BigDecimal("1300"), new BigDecimal("1350"), new BigDecimal("1400"));

        assertThat(range.low()).isEqualByComparingTo("1300");
        assertThat(range.base()).isEqualByComparingTo("1350");
        assertThat(range.high()).isEqualByComparingTo("1400");
    }

    @Test
    @DisplayName("세 값이 같아도 유효하다 — Forecast 가 없을 때 기준 환율만으로 계산한다")
    void constructor_AllEqual_IsValid() {
        assertThatCode(() -> new RateRange(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("하단이 기준보다 크면 거부한다")
    void constructor_LowAboveBase_Throws() {
        assertThatThrownBy(() -> new RateRange(
                new BigDecimal("1400"), new BigDecimal("1350"), new BigDecimal("1400")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low <= base <= high");
    }

    @Test
    @DisplayName("기준이 상단보다 크면 거부한다")
    void constructor_BaseAboveHigh_Throws() {
        assertThatThrownBy(() -> new RateRange(
                new BigDecimal("1300"), new BigDecimal("1400"), new BigDecimal("1350")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low <= base <= high");
    }

    @Test
    @DisplayName("환율이 0 이하면 거부한다")
    void constructor_NonPositiveRate_Throws() {
        assertThatThrownBy(() -> new RateRange(
                BigDecimal.ZERO, new BigDecimal("1350"), new BigDecimal("1400")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("null 환율은 거부한다")
    void constructor_Null_Throws() {
        BigDecimal rate = new BigDecimal("1350");
        assertThatThrownBy(() -> new RateRange(null, rate, rate))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RateRange(rate, null, rate))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RateRange(rate, rate, null))
                .isInstanceOf(NullPointerException.class);
    }
}
