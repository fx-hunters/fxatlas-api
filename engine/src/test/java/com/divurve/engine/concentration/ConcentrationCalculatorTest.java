package com.divurve.engine.concentration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcentrationCalculatorTest {

    private ConcentrationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConcentrationCalculator();
    }

    @Test
    void calculateConcentration_SingleCurrency_Returns1() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 1000.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_EqualDistribution_Returns025() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 250.0);
        holdings.put("EUR", 250.0);
        holdings.put("GBP", 250.0);
        holdings.put("JPY", 250.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isCloseTo(0.25, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_Unequal_Between() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 600.0);
        holdings.put("EUR", 400.0);

        double concentration = calculator.calculateConcentration(holdings);

        // (0.6^2 + 0.4^2) = 0.36 + 0.16 = 0.52
        assertThat(concentration).isCloseTo(0.52, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_Empty_Returns0() {
        Map<String, Double> holdings = new HashMap<>();

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isEqualTo(0.0);
    }

    @Test
    void calculateConcentration_Null_ThrowsNullPointerException() {
        assertThatThrownBy(() -> calculator.calculateConcentration(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void calculateConcentration_ZeroTotal_Returns0() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 0.0);
        holdings.put("EUR", 0.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isEqualTo(0.0);
    }

    @Test
    void verdictConcentrationChange_Improves() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.40);

        assertThat(verdict).isEqualTo("improves");
    }

    @Test
    void verdictConcentrationChange_Worsens() {
        String verdict = calculator.verdictConcentrationChange(0.40, 0.50);

        assertThat(verdict).isEqualTo("worsens");
    }

    @Test
    void verdictConcentrationChange_Neutral_SmallDelta() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.51);

        assertThat(verdict).isEqualTo("neutral");
    }

    @Test
    void verdictConcentrationChange_Neutral_JustAtThreshold() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.515);

        assertThat(verdict).isEqualTo("neutral");
    }

    @Test
    void report_ValidInputs_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 600.0);
        before.put("EUR", 400.0);

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 700.0);
        after.put("EUR", 300.0);

        var report = calculator.report(before, after);

        assertThat(report.verdict()).isEqualTo("worsens");
        assertThat(report.threshold()).isEqualTo(0.02);
    }

    @Test
    void report_Null_ThrowsNullPointerException() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 1000.0);

        assertThatThrownBy(() -> calculator.report(null, holdings))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> calculator.report(holdings, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void report_NormalizedProportions() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 100.0);
        before.put("EUR", 100.0);

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 200.0);
        after.put("EUR", 300.0);

        var report = calculator.report(before, after);

        // 정규화 확인
        assertThat(report.before().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
        assertThat(report.after().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
    }
}
