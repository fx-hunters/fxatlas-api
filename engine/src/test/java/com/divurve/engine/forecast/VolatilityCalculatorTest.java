package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolatilityCalculatorTest {

    @Test
    void testCalculateRealized30d_HappyPath() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            returns.add(0.01); // 1% 일일 수익률
        }
        double vol = VolatilityCalculator.calculateRealized30d(returns);
        assertTrue(vol > 0);
        assertTrue(Double.isFinite(vol));
    }

    @Test
    void testCalculateRealized30d_InsufficientData() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            returns.add(0.01);
        }
        assertThrows(IllegalArgumentException.class, () -> VolatilityCalculator.calculateRealized30d(returns));
    }

    @Test
    void testCalculateRealized30d_NullInput() {
        assertThrows(NullPointerException.class, () -> VolatilityCalculator.calculateRealized30d(null));
    }

    @Test
    void testCalculateRealized30d_ZeroVolatility() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            returns.add(0.0); // 0 수익률
        }
        double vol = VolatilityCalculator.calculateRealized30d(returns);
        assertEquals(0.0, vol, 1e-6);
    }

    @Test
    void testCalculatePercentile5y_HappyPath() {
        List<Double> returns = createLongReturnsSequence(5 * 252 + 30, 0.01);
        int percentile = VolatilityCalculator.calculatePercentile5y(returns);
        assertTrue(percentile >= 0 && percentile <= 100);
    }

    @Test
    void testCalculatePercentile5y_InsufficientData() {
        List<Double> returns = createLongReturnsSequence(5 * 252 + 29, 0.01);
        assertThrows(IllegalArgumentException.class, () -> VolatilityCalculator.calculatePercentile5y(returns));
    }

    @Test
    void testCalculatePercentile5y_LowVolatility() {
        List<Double> returns = createLongReturnsSequence(5 * 252 + 30, 0.001);
        int percentile = VolatilityCalculator.calculatePercentile5y(returns);
        assertTrue(percentile < 50); // 낮은 변동성은 낮은 백분위
    }

    @Test
    void testCalculatePercentile5y_HighVolatility() {
        List<Double> returns = new ArrayList<>();
        // 처음 5년: 낮은 변동성
        for (int i = 0; i < 5 * 252; i++) {
            returns.add(0.001);
        }
        // 마지막 30일: 높은 변동성
        for (int i = 0; i < 30; i++) {
            returns.add(0.05);
        }
        int percentile = VolatilityCalculator.calculatePercentile5y(returns);
        assertTrue(percentile > 50); // 높은 변동성은 높은 백분위
    }

    @Test
    void testClassifyRegime_Low() {
        assertEquals("Low", VolatilityCalculator.classifyRegime(20));
    }

    @Test
    void testClassifyRegime_Normal() {
        assertEquals("Normal", VolatilityCalculator.classifyRegime(50));
    }

    @Test
    void testClassifyRegime_High() {
        assertEquals("High", VolatilityCalculator.classifyRegime(80));
    }

    @Test
    void testClassifyRegime_Boundary33() {
        assertEquals("Normal", VolatilityCalculator.classifyRegime(33));
    }

    @Test
    void testClassifyRegime_Boundary67() {
        assertEquals("High", VolatilityCalculator.classifyRegime(67));
    }

    private List<Double> createLongReturnsSequence(int count, double value) {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            returns.add(value);
        }
        return returns;
    }
}
