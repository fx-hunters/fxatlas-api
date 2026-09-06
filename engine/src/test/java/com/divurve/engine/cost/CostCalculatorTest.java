package com.divurve.engine.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

    private CostCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CostCalculator();
    }

    @Test
    void totalCost_CalculatesCorrectly() {
        long spread = calculator.spreadCost(1000000.0, 0.0035);
        long fixed = calculator.fixedCost(4, 3000);
        long total = calculator.totalCost(1000000.0, 0.0035, 4, 3000);

        assertThat(total).isEqualTo(spread + fixed);
    }

    @Test
    void totalCost_ZeroAmount_ReturnsFixedCostOnly() {
        long total = calculator.totalCost(0.0, 0.0035, 4, 3000);

        assertThat(total).isEqualTo(12000L);
    }

    @Test
    void totalCost_ZeroSpread_ReturnsFixedCostOnly() {
        long total = calculator.totalCost(1000000.0, 0.0, 4, 3000);

        assertThat(total).isEqualTo(12000L);
    }

    @Test
    void spreadCost_CalculatesCorrectly() {
        long spread = calculator.spreadCost(1000000.0, 0.0035);

        assertThat(spread).isEqualTo(3500L);
    }

    @Test
    void spreadCost_NegativeAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.spreadCost(-1000.0, 0.0035))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void spreadCost_NegativeSpreadRatio_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.spreadCost(1000.0, -0.001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0~1.0");
    }

    @Test
    void spreadCost_OverOneSpreadRatio_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.spreadCost(1000.0, 1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0~1.0");
    }

    @Test
    void fixedCost_CalculatesCorrectly() {
        long fixed = calculator.fixedCost(4, 3000);

        assertThat(fixed).isEqualTo(12000L);
    }

    @Test
    void fixedCost_ZeroSteps_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.fixedCost(0, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void fixedCost_NegativeFee_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.fixedCost(4, -100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void totalCost_ValidInputs_ReturnsPositive() {
        long total = calculator.totalCost(500000.0, 0.005, 8, 2500);

        assertThat(total).isPositive();
    }

    @Test
    void totalCost_NegativeAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(-1000.0, 0.005, 4, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void totalCost_NegativeFee_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(1000.0, 0.005, 4, -100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void totalCost_NegativeSpreadRatio_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(1000.0, -0.001, 4, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0~1.0");
    }

    @Test
    void totalCost_OverOneSpreadRatio_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(1000.0, 1.5, 4, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0~1.0");
    }

    @Test
    void totalCost_ZeroSplitCount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(1000.0, 0.005, 0, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void totalCost_NegativeSplitCount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.totalCost(1000.0, 0.005, -1, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void spreadCost_ZeroAmount_ReturnsZero() {
        long spread = calculator.spreadCost(0.0, 0.005);

        assertThat(spread).isEqualTo(0L);
    }

    @Test
    void fixedCost_ZeroFee_ReturnsZero() {
        long fixed = calculator.fixedCost(5, 0);

        assertThat(fixed).isEqualTo(0L);
    }
}
