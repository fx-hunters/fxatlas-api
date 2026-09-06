package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelPerformanceCalculatorTest {

    @Test
    void testCalculateHitRate_PerfectPrediction() {
        List<Double> predictions = List.of(1200.0, 1210.0, 1220.0);
        List<Double> actuals = List.of(1200.0, 1210.0, 1220.0);

        double hitRate = ModelPerformanceCalculator.calculateHitRate(predictions, actuals);
        assertEquals(1.0, hitRate, 1e-6);
    }

    @Test
    void testCalculateHitRate_NoMatch() {
        List<Double> predictions = List.of(1200.0, 1190.0, 1180.0);
        List<Double> actuals = List.of(1200.0, 1210.0, 1220.0);

        double hitRate = ModelPerformanceCalculator.calculateHitRate(predictions, actuals);
        assertTrue(hitRate >= 0 && hitRate <= 1.0);
    }

    @Test
    void testCalculateHitRate_MismatchedSizes() {
        List<Double> predictions = List.of(1200.0, 1210.0);
        List<Double> actuals = List.of(1200.0, 1210.0, 1220.0);

        assertThrows(IllegalArgumentException.class,
            () -> ModelPerformanceCalculator.calculateHitRate(predictions, actuals));
    }

    @Test
    void testCalculateHitRate_Empty() {
        List<Double> predictions = new ArrayList<>();
        List<Double> actuals = new ArrayList<>();

        double hitRate = ModelPerformanceCalculator.calculateHitRate(predictions, actuals);
        assertEquals(0.0, hitRate);
    }

    @Test
    void testCalculateHitRate_NullInput() {
        assertThrows(NullPointerException.class,
            () -> ModelPerformanceCalculator.calculateHitRate(null, List.of(1200.0)));
        assertThrows(NullPointerException.class,
            () -> ModelPerformanceCalculator.calculateHitRate(List.of(1200.0), null));
    }

    @Test
    void testCalculateMae_HappyPath() {
        List<Double> predictions = List.of(1200.0, 1210.0, 1220.0);
        List<Double> actuals = List.of(1210.0, 1220.0, 1230.0);

        double mae = ModelPerformanceCalculator.calculateMae(predictions, actuals);
        assertEquals(10.0, mae, 1e-6);
    }

    @Test
    void testCalculateMae_ZeroError() {
        List<Double> predictions = List.of(1200.0, 1210.0);
        List<Double> actuals = List.of(1200.0, 1210.0);

        double mae = ModelPerformanceCalculator.calculateMae(predictions, actuals);
        assertEquals(0.0, mae, 1e-6);
    }

    @Test
    void testCalculateMae_Empty() {
        double mae = ModelPerformanceCalculator.calculateMae(new ArrayList<>(), new ArrayList<>());
        assertEquals(0.0, mae);
    }

    @Test
    void testCalculateCoverage80_AllInside() {
        List<Double> lowerBounds = List.of(1150.0, 1160.0, 1170.0);
        List<Double> upperBounds = List.of(1250.0, 1260.0, 1270.0);
        List<Double> actuals = List.of(1200.0, 1210.0, 1220.0);

        double coverage = ModelPerformanceCalculator.calculateCoverage80(lowerBounds, upperBounds, actuals);
        assertEquals(1.0, coverage, 1e-6);
    }

    @Test
    void testCalculateCoverage80_NoneInside() {
        List<Double> lowerBounds = List.of(1300.0, 1310.0);
        List<Double> upperBounds = List.of(1400.0, 1410.0);
        List<Double> actuals = List.of(1200.0, 1210.0);

        double coverage = ModelPerformanceCalculator.calculateCoverage80(lowerBounds, upperBounds, actuals);
        assertEquals(0.0, coverage, 1e-6);
    }

    @Test
    void testCalculateCoverage80_PartialInside() {
        List<Double> lowerBounds = List.of(1150.0, 1300.0);
        List<Double> upperBounds = List.of(1250.0, 1400.0);
        List<Double> actuals = List.of(1200.0, 1250.0);

        double coverage = ModelPerformanceCalculator.calculateCoverage80(lowerBounds, upperBounds, actuals);
        assertEquals(0.5, coverage, 1e-6);
    }

    @Test
    void testCalculateAvgWidth_HappyPath() {
        List<Double> lowerBounds = List.of(1150.0, 1160.0);
        List<Double> upperBounds = List.of(1250.0, 1260.0);

        double avgWidth = ModelPerformanceCalculator.calculateAvgWidth(lowerBounds, upperBounds);
        assertEquals(100.0, avgWidth, 1e-6); // (100 + 100) / 2 = 100
    }

    @Test
    void testCalculateAvgWidth_Empty() {
        double avgWidth = ModelPerformanceCalculator.calculateAvgWidth(new ArrayList<>(), new ArrayList<>());
        assertEquals(0.0, avgWidth);
    }

    @Test
    void testCalculateRandomWalkBenchmark() {
        List<Double> actuals = List.of(1200.0, 1210.0, 1220.0);
        ModelPerformanceCalculator.RandomWalkMetrics metrics =
            ModelPerformanceCalculator.calculateRandomWalkBenchmark(1200.0, actuals);

        assertTrue(metrics.hitRate() >= 0 && metrics.hitRate() <= 1.0);
        assertTrue(metrics.mae() >= 0);
    }

    @Test
    void testCalculateImprovement_Better() {
        double improvement = ModelPerformanceCalculator.calculateImprovement(10.0, 15.0);
        assertEquals(1.0 / 3.0, improvement, 1e-6); // (15 - 10) / 15 ≈ 0.333
    }

    @Test
    void testCalculateImprovement_Worse() {
        double improvement = ModelPerformanceCalculator.calculateImprovement(20.0, 15.0);
        assertEquals(-1.0 / 3.0, improvement, 1e-6); // (15 - 20) / 15 ≈ -0.333
    }

    @Test
    void testCalculateImprovement_ZeroBenchmark() {
        double improvement = ModelPerformanceCalculator.calculateImprovement(10.0, 0.0);
        assertEquals(0.0, improvement);
    }
}
