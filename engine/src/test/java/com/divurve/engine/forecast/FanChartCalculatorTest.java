package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FanChartCalculatorTest {

    @Test
    void testGeneratePaths_HappyPath() {
        List<List<Double>> paths = new ArrayList<>();
        paths.add(List.of(100.0, 101.0, 102.0));
        paths.add(List.of(100.0, 99.0, 98.0));
        paths.add(List.of(100.0, 100.5, 101.5));

        List<FanChartCalculator.PathPoint> result = FanChartCalculator.generatePaths(paths);

        assertEquals(3, result.size());
        // 첫 시점: 모두 100
        assertEquals(100.0, result.get(0).p50Lo(), 1e-6);
        assertEquals(100.0, result.get(0).p50Hi(), 1e-6);
    }

    @Test
    void testGeneratePaths_Empty() {
        List<List<Double>> paths = new ArrayList<>();
        List<FanChartCalculator.PathPoint> result = FanChartCalculator.generatePaths(paths);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGeneratePaths_NullInput() {
        assertThrows(NullPointerException.class, () -> FanChartCalculator.generatePaths(null));
    }

    @Test
    void testGenerateBaseLine_HappyPath() {
        List<Double> baseline = FanChartCalculator.generateBaseLine(1200.0, 30);
        assertEquals(31, baseline.size()); // 0일부터 30일까지
        for (Double rate : baseline) {
            assertEquals(1200.0, rate, 1e-6);
        }
    }

    @Test
    void testGenerateBaseLine_InvalidHorizon() {
        assertThrows(IllegalArgumentException.class, () -> FanChartCalculator.generateBaseLine(1200.0, 0));
        assertThrows(IllegalArgumentException.class, () -> FanChartCalculator.generateBaseLine(1200.0, -1));
    }

    @Test
    void testInterval80WidthHighPct() {
        double result = FanChartCalculator.interval80WidthHighPct(1200.0, 1260.0);
        assertEquals(0.05, result, 1e-6); // 5% 상승
    }

    @Test
    void testInterval80WidthLowPct() {
        double result = FanChartCalculator.interval80WidthLowPct(1200.0, 1140.0);
        assertEquals(0.05, result, 1e-6); // 5% 하락
    }

    @Test
    void testInterval80VsThreeYearAvg() {
        double result = FanChartCalculator.interval80VsThreeYearAvg(1260.0, 1140.0, 1200.0);
        assertEquals(0.1, result, 1e-6); // 폭 120 / 평균 1200 = 10%
    }

    @Test
    void testGeneratePaths_Quartiles() {
        List<List<Double>> paths = new ArrayList<>();
        // 10개 경로, 각각 100~109
        for (int i = 0; i < 10; i++) {
            paths.add(List.of((double) (100 + i), (double) (101 + i)));
        }

        List<FanChartCalculator.PathPoint> result = FanChartCalculator.generatePaths(paths);
        assertEquals(2, result.size());

        // 첫 시점: 100~109
        // p50Lo ≈ 102.25 (하위 25%)
        // p50Hi ≈ 106.75 (상위 25%)
        assertTrue(result.get(0).p50Lo() >= 100 && result.get(0).p50Lo() <= 105);
        assertTrue(result.get(0).p50Hi() >= 105 && result.get(0).p50Hi() <= 109);
    }

    @Test
    void testPercentile_SingleValue() {
        List<List<Double>> paths = new ArrayList<>();
        paths.add(List.of(100.0));
        List<FanChartCalculator.PathPoint> result = FanChartCalculator.generatePaths(paths);
        assertEquals(1, result.size());
        assertEquals(100.0, result.get(0).p50Lo(), 1e-6);
    }
}
