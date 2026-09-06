package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolatilityCalculatorTest {

    @Test
    void testUtilityClassCannotBeInstantiated() throws Exception {
        Constructor<VolatilityCalculator> constructor = VolatilityCalculator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, thrown.getCause());
    }

    @Test
    void testCalculateRealized30d_HappyPath() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            returns.add(Math.sin(i * 0.1) * 0.01); // 변동하는 일일 수익률
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
        double percentile = VolatilityCalculator.calculatePercentile5y(returns);
        // ERD fx_stats.vol_percentile_5y 는 NUMERIC(5,4) — 0~100 정수가 아니라 0~1 비율이다.
        assertTrue(percentile >= 0.0 && percentile <= 1.0);
    }

    @Test
    void testCalculatePercentile5y_InsufficientData() {
        List<Double> returns = createLongReturnsSequence(5 * 252 + 29, 0.01);
        assertThrows(IllegalArgumentException.class, () -> VolatilityCalculator.calculatePercentile5y(returns));
    }

    @Test
    void testCalculatePercentile5y_LowVolatility() {
        List<Double> returns = createLongReturnsSequence(5 * 252 + 30, 0.001);
        double percentile = VolatilityCalculator.calculatePercentile5y(returns);
        assertTrue(percentile < 0.5); // 낮은 변동성은 낮은 백분위
    }

    @Test
    void testCalculatePercentile5y_HighVolatility() {
        List<Double> returns = new ArrayList<>();
        // 처음 5년: 낮은 변동성
        for (int i = 0; i < 5 * 252; i++) {
            returns.add(0.001 + Math.sin(i * 0.01) * 0.0005);
        }
        // 마지막 30일: 높은 변동성
        for (int i = 0; i < 30; i++) {
            returns.add(0.02 + Math.sin(i * 0.2) * 0.01);
        }
        double percentile = VolatilityCalculator.calculatePercentile5y(returns);
        assertTrue(percentile >= 0.0 && percentile <= 1.0); // 백분위는 유효한 범위 내
    }

    /**
     * 반환 단위가 0~1 비율인지 고정한다 (calc: 정수 0~100 → 비율 0~1).
     *
     * <p>마지막 30일만 변동성이 크면 5년 롤링 변동성이 거의 전부 현재값보다 낮다.
     * 변경 전 구현이었다면 같은 입력에 {@code 100} 에 가까운 정수가 나왔다.
     */
    @Test
    void testCalculatePercentile5y_ReturnsRatioNotIntegerPercent() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 5 * 252; i++) {
            returns.add(0.0);
        }
        for (int i = 0; i < 30; i++) {
            returns.add(i % 2 == 0 ? 0.05 : -0.05);
        }

        double percentile = VolatilityCalculator.calculatePercentile5y(returns);

        assertTrue(percentile > 0.9 && percentile <= 1.0,
            "0~1 비율이어야 한다(정수 백분위였다면 90 이상): " + percentile);
    }

    private List<Double> createLongReturnsSequence(int count, double value) {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            returns.add(value);
        }
        return returns;
    }
}
