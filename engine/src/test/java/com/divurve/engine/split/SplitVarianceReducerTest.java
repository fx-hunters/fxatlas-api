package com.divurve.engine.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitVarianceReducerTest {

    private SplitVarianceReducer reducer;

    @BeforeEach
    void setUp() {
        reducer = new SplitVarianceReducer();
    }

    @Test
    void gFactor_N1_Returns1000() {
        double result = reducer.gFactor(1);
        assertThat(result).isCloseTo(1.000, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_N2_Returns0791() {
        double result = reducer.gFactor(2);
        assertThat(result).isCloseTo(0.791, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_N4_Returns0685() {
        double result = reducer.gFactor(4);
        assertThat(result).isCloseTo(0.685, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_N6_Returns0649() {
        double result = reducer.gFactor(6);
        assertThat(result).isCloseTo(0.649, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_N8_Returns0632() {
        double result = reducer.gFactor(8);
        assertThat(result).isCloseTo(0.632, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_N10_Returns0620() {
        double result = reducer.gFactor(10);
        assertThat(result).isCloseTo(0.620, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void gFactor_Monotonic() {
        double g1 = reducer.gFactor(1);
        double g4 = reducer.gFactor(4);
        double g10 = reducer.gFactor(10);

        assertThat(g1).isGreaterThan(g4);
        assertThat(g4).isGreaterThan(g10);
    }

    @Test
    void gFactor_Zero_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> reducer.gFactor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("분할 횟수는 1 이상");
    }

    @Test
    void gFactor_TooLarge_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> reducer.gFactor(53))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("분할 횟수는 1 이상 52 이하");
    }

    @Test
    void sigmaGain_N2_Returns0209() {
        double gain = reducer.sigmaGain(2);
        assertThat(gain).isCloseTo(0.209, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void sigmaGain_Positive() {
        double gain = reducer.sigmaGain(5);
        assertThat(gain).isGreaterThan(0.0);
    }

    @Test
    void sigmaGain_LessThan2_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> reducer.sigmaGain(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("분할 횟수 2 이상");
    }
}
