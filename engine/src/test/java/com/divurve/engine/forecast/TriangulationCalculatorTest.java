package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriangulationCalculatorTest {

    @Test
    void testTriangulateRates_HappyPath() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);
        baseRates.put("USD_JPY", 110.0);
        baseRates.put("EUR_USD", 1.1);

        Map<String, Double> result = TriangulationCalculator.triangulateRates(baseRates);

        assertEquals(1200.0, result.get("USD_KRW"), 1e-6);
        assertEquals(110.0, result.get("USD_JPY"), 1e-6);
        assertEquals(1.1, result.get("EUR_USD"), 1e-6);

        // 유도된 환율들
        assertEquals(1320.0, result.get("EUR_KRW"), 1e-6); // 1.1 * 1200
        assertEquals(121.0, result.get("EUR_JPY"), 1e-6); // 1.1 * 110
    }

    @Test
    void testTriangulateRates_MissingUsdKrw() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_JPY", 110.0);
        baseRates.put("EUR_USD", 1.1);

        assertThrows(IllegalArgumentException.class, () -> TriangulationCalculator.triangulateRates(baseRates));
    }

    @Test
    void testTriangulateRates_MissingUsdJpy() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);
        baseRates.put("EUR_USD", 1.1);

        assertThrows(IllegalArgumentException.class, () -> TriangulationCalculator.triangulateRates(baseRates));
    }

    @Test
    void testTriangulateRates_MissingEurUsd() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);
        baseRates.put("USD_JPY", 110.0);

        assertThrows(IllegalArgumentException.class, () -> TriangulationCalculator.triangulateRates(baseRates));
    }

    @Test
    void testTriangulateRates_WithGbpUsd() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);
        baseRates.put("USD_JPY", 110.0);
        baseRates.put("EUR_USD", 1.1);
        baseRates.put("GBP_USD", 1.3);

        Map<String, Double> result = TriangulationCalculator.triangulateRates(baseRates);

        assertEquals(1560.0, result.get("GBP_KRW"), 1e-6); // 1.3 * 1200
        assertEquals(143.0, result.get("GBP_JPY"), 1e-6); // 1.3 * 110
    }

    @Test
    void testTriangulateRates_NullInput() {
        assertThrows(NullPointerException.class, () -> TriangulationCalculator.triangulateRates(null));
    }

    @Test
    void testCalculateCrossRate_Standard() {
        double rate = TriangulationCalculator.calculateCrossRate(1200.0, 110.0, false, false);
        assertEquals(1200.0 / 110.0, rate, 1e-6);
    }

    @Test
    void testCalculateCrossRate_Reverse1() {
        double rate = TriangulationCalculator.calculateCrossRate(1200.0, 110.0, true, false);
        assertEquals((1.0 / 1200.0) / 110.0, rate, 1e-6);
    }

    @Test
    void testCalculateCrossRate_Reverse2() {
        double rate = TriangulationCalculator.calculateCrossRate(1200.0, 110.0, false, true);
        assertEquals(1200.0 / (1.0 / 110.0), rate, 1e-6);
    }

    @Test
    void testCalculateCrossRate_BothReverse() {
        double rate = TriangulationCalculator.calculateCrossRate(1200.0, 110.0, true, true);
        assertEquals((1.0 / 1200.0) / (1.0 / 110.0), rate, 1e-6);
    }

    @Test
    void testCalculateCrossRate_InvalidRate() {
        assertThrows(IllegalArgumentException.class,
            () -> TriangulationCalculator.calculateCrossRate(0, 110.0, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> TriangulationCalculator.calculateCrossRate(1200.0, 0, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> TriangulationCalculator.calculateCrossRate(-1200.0, 110.0, false, false));
    }

    @Test
    void testInvertRate_HappyPath() {
        double inverted = TriangulationCalculator.invertRate(1200.0);
        assertEquals(1.0 / 1200.0, inverted, 1e-6);
    }

    @Test
    void testInvertRate_InvalidRate() {
        assertThrows(IllegalArgumentException.class, () -> TriangulationCalculator.invertRate(0));
        assertThrows(IllegalArgumentException.class, () -> TriangulationCalculator.invertRate(-100));
    }
}
