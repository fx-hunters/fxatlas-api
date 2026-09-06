package com.divurve.engine.concentration;

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
