package com.divurve.engine.concentration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcentrationCalculator")
class ConcentrationCalculatorTest {

    private ConcentrationCalculator calculator;

    @BeforeEach
    void setup() {
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

    @Test
    void report_EmptyBefore_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 1000.0);

        var report = calculator.report(before, after);

        assertThat(report.before()).isEmpty();
        assertThat(report.after()).hasSize(1);
        assertThat(report.verdict()).isEqualTo("worsens");
    }

    @Test
    void report_EmptyAfter_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 1000.0);

        Map<String, Double> after = new HashMap<>();

        var report = calculator.report(before, after);

        assertThat(report.before()).hasSize(1);
        assertThat(report.after()).isEmpty();
        assertThat(report.verdict()).isEqualTo("improves");
    }

    @Test
    @DisplayName("집중도 진단: 안전 상태 (주력 통화 < 임계값)")
    void testDiagnose_Safe() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);
        assets.put("EUR", 200000L);
        assets.put("JPY", 500000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.4);

        assertNotNull(result);
        assertEquals("JPY", result.topCurrency());
        assertEquals(0.5, result.topShare(), 0.0001);
        assertEquals(0.4, result.threshold());
        assertEquals("warning", result.status()); // 50% > 40% 이므로 경고
    }

    @Test
    @DisplayName("집중도 진단: 경고 상태 (주력 통화 > 임계값)")
    void testDiagnose_Warning() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 200000L);
        assets.put("EUR", 300000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.4);

        assertNotNull(result);
        assertEquals("EUR", result.topCurrency());
        assertEquals(0.6, result.topShare(), 0.0001);
        assertEquals("warning", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 정확히 임계값")
    void testDiagnose_ExactlyThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 400000L);
        assets.put("EUR", 600000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.6);

        assertNotNull(result);
        assertEquals("EUR", result.topCurrency());
        assertEquals(0.6, result.topShare(), 0.0001);
        assertEquals("safe", result.status()); // 60% == 60% 이므로 안전
    }

    @Test
    @DisplayName("집중도 진단: 단일 통화")
    void testDiagnose_SingleCurrency() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 1000000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.5);

        assertNotNull(result);
        assertEquals("USD", result.topCurrency());
        assertEquals(1.0, result.topShare(), 0.0001);
        assertEquals("warning", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 빈 포트폴리오")
    void testDiagnose_EmptyPortfolio() {
        Map<String, Long> assets = new HashMap<>();

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.5);

        assertNotNull(result);
        assertNull(result.topCurrency());
        assertEquals(0.0, result.topShare());
        assertEquals("safe", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 모두 0 자산")
    void testDiagnose_ZeroAssets() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 0L);
        assets.put("EUR", 0L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.5);

        assertNotNull(result);
        assertEquals("safe", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 임계값 0")
    void testDiagnose_ZeroThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.0);

        assertNotNull(result);
        assertEquals("warning", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 임계값 1")
    void testDiagnose_FullThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 1.0);

        assertNotNull(result);
        assertEquals("safe", result.status());
    }

    @Test
    @DisplayName("집중도 진단: 음수 임계값 예외")
    void testDiagnose_NegativeThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.diagnose(assets, -0.1));
    }

    @Test
    @DisplayName("집중도 진단: 1초과 임계값 예외")
    void testDiagnose_OverThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.diagnose(assets, 1.5));
    }

    @Test
    @DisplayName("집중도 진단: null 맵 예외")
    void testDiagnose_NullMap() {
        assertThrows(NullPointerException.class, () ->
                calculator.diagnose(null, 0.5));
    }

    @Test
    @DisplayName("정렬된 노출도: 내림차순")
    void testGetSortedExposure_Descending() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);
        assets.put("EUR", 200000L);
        assets.put("JPY", 500000L);

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        // LinkedHashMap이므로 삽입 순서 확인 필요 (내림차순)
        var iterator = sorted.keySet().iterator();
        String first = iterator.next();
        assertEquals("JPY", first); // 500000 (가장 큼)
    }

    @Test
    @DisplayName("정렬된 노출도: 빈 맵")
    void testGetSortedExposure_Empty() {
        Map<String, Long> assets = new HashMap<>();

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        assertEquals(0, sorted.size());
    }

    @Test
    @DisplayName("정렬된 노출도: 자산 0")
    void testGetSortedExposure_ZeroAssets() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 0L);
        assets.put("EUR", 0L);

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        assertEquals(0, sorted.size());
    }

    @Test
    @DisplayName("정렬된 노출도: null 맵 예외")
    void testGetSortedExposure_NullMap() {
        assertThrows(NullPointerException.class, () ->
                calculator.getSortedExposure(null));
    }
}
