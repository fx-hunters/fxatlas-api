package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GbmSimulatorTest {

    @Test
    void testSimulate_HappyPath() {
        GbmSimulator simulator = new GbmSimulator(42L);
        List<List<Double>> paths = simulator.simulate(1200.0, 0.05, 0.15, 30, 100);

        assertEquals(100, paths.size()); // numPaths
        for (List<Double> path : paths) {
            assertEquals(31, path.size()); // horizonDays + 1
            assertEquals(1200.0, path.get(0), 1e-6); // 모든 경로 시작값이 같아야 함
            assertTrue(path.stream().allMatch(r -> r > 0)); // 모든 값 양수
        }
    }

    @Test
    void testSimulate_Reproducibility() {
        GbmSimulator sim1 = new GbmSimulator(42L);
        List<List<Double>> paths1 = sim1.simulate(1200.0, 0.05, 0.15, 10, 2);

        GbmSimulator sim2 = new GbmSimulator(42L);
        List<List<Double>> paths2 = sim2.simulate(1200.0, 0.05, 0.15, 10, 2);

        for (int i = 0; i < paths1.size(); i++) {
            for (int j = 0; j < paths1.get(i).size(); j++) {
                assertEquals(paths1.get(i).get(j), paths2.get(i).get(j), 1e-10);
            }
        }
    }

    @Test
    void testSimulate_DifferentSeeds() {
        GbmSimulator sim1 = new GbmSimulator(42L);
        List<List<Double>> paths1 = sim1.simulate(1200.0, 0.05, 0.15, 10, 2);

        GbmSimulator sim2 = new GbmSimulator(43L);
        List<List<Double>> paths2 = sim2.simulate(1200.0, 0.05, 0.15, 10, 2);

        boolean different = false;
        for (int i = 0; i < paths1.size() && !different; i++) {
            for (int j = 0; j < paths1.get(i).size() && !different; j++) {
                if (Math.abs(paths1.get(i).get(j) - paths2.get(i).get(j)) > 1e-6) {
                    different = true;
                }
            }
        }
        assertTrue(different); // 다른 시드는 다른 경로 생성
    }

    @Test
    void testSimulate_InvalidInitialRate() {
        GbmSimulator simulator = new GbmSimulator(42L);
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(0, 0.05, 0.15, 30, 100));
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(-100, 0.05, 0.15, 30, 100));
    }

    @Test
    void testSimulate_InvalidVolatility() {
        GbmSimulator simulator = new GbmSimulator(42L);
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, 0, 30, 100));
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, -0.1, 30, 100));
    }

    @Test
    void testSimulate_InvalidHorizonDays() {
        GbmSimulator simulator = new GbmSimulator(42L);
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, 0.15, 0, 100));
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, 0.15, -1, 100));
    }

    @Test
    void testSimulate_InvalidNumPaths() {
        GbmSimulator simulator = new GbmSimulator(42L);
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, 0.15, 30, 0));
        assertThrows(IllegalArgumentException.class,
            () -> simulator.simulate(1200.0, 0.05, 0.15, 30, -1));
    }

    @Test
    void testSimulate_MeanReversion() {
        GbmSimulator simulator = new GbmSimulator(42L);
        List<List<Double>> paths = simulator.simulate(1200.0, 0.0, 0.15, 252, 1000);

        // 드리프트 0이면 평균은 초기값 근처여야 함 (로그 정규분포의 특성상 약간의 편향 가능)
        double meanFinalValue = paths.stream()
            .mapToDouble(p -> p.get(p.size() - 1))
            .average()
            .orElse(0);

        // 초기값의 ±20% 범위 내여야 함
        assertTrue(meanFinalValue > 1200.0 * 0.8 && meanFinalValue < 1200.0 * 1.2);
    }

    @Test
    void testSimulateZeroDrift_Equivalence() {
        GbmSimulator simulator = new GbmSimulator(42L);
        List<List<Double>> paths = simulator.simulateZeroDrift(1200.0, 0.15, 30, 10);

        assertEquals(10, paths.size());
        for (List<Double> path : paths) {
            assertEquals(31, path.size());
            assertEquals(1200.0, path.get(0), 1e-6);
            assertTrue(path.stream().allMatch(r -> r > 0));
        }
    }

    @Test
    void testSimulate_PathMonotonicity() {
        GbmSimulator simulator = new GbmSimulator(42L);
        List<List<Double>> paths = simulator.simulate(1200.0, 0.0, 0.01, 10, 1);

        // 변동성이 매우 낮으면 경로가 초기값 근처에 머물러야 함
        for (Double rate : paths.get(0)) {
            assertTrue(rate > 1190 && rate < 1210);
        }
    }
}
