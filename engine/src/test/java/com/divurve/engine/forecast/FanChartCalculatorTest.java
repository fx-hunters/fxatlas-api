package com.divurve.engine.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FanChartCalculator} 검증 (명세 v2 §5.7 {@code band}).
 *
 * <p>응답 필드명이 {@code path} → {@code band} 로 바뀌었어도 계산 계약은 같다 —
 * 각 시점의 50퍼센트·80퍼센트 구간 경계다. 여기서는 <b>해석적 구간</b>({@code analyticInterval})을
 * 특히 못박는다. 모델 성적표와 {@code /forecast} 가 같은 입력에 같은 구간을 내야 하기 때문이다.
 */
@DisplayName("FanChartCalculator")
class FanChartCalculatorTest {

    @Test
    @DisplayName("유틸리티 클래스라 인스턴스를 만들 이유가 없다")
    void privateConstructor() throws Exception {
        Constructor<FanChartCalculator> constructor = FanChartCalculator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    @Test
    @DisplayName("경로점: 분위수 인덱스가 정수인 경우와 보간이 필요한 경우 모두 계산한다")
    void generatePaths() {
        // 경로 5개 × 시점 2개. 시점별 값이 1..5 라 p50 은 정수 인덱스(2·4번째), p80 은 보간이 필요하다.
        List<List<Double>> paths = List.of(
                List.of(1.0, 10.0),
                List.of(2.0, 20.0),
                List.of(3.0, 30.0),
                List.of(4.0, 40.0),
                List.of(5.0, 50.0)
        );

        List<FanChartCalculator.PathPoint> points = FanChartCalculator.generatePaths(paths);

        assertEquals(2, points.size());
        FanChartCalculator.PathPoint first = points.get(0);
        assertEquals(2.0, first.p50Lo(), 1e-12);  // index 0.25*4 = 1.0 (정수)
        assertEquals(4.0, first.p50Hi(), 1e-12);  // index 0.75*4 = 3.0 (정수)
        assertEquals(1.4, first.p80Lo(), 1e-12);  // index 0.10*4 = 0.4 (보간)
        assertEquals(4.6, first.p80Hi(), 1e-12);  // index 0.90*4 = 3.6 (보간)
    }

    @Test
    @DisplayName("경로점: 시뮬레이션이 비어 있으면 빈 목록")
    void generatePathsEmpty() {
        assertTrue(FanChartCalculator.generatePaths(List.of()).isEmpty());
    }

    @Test
    @DisplayName("경로점: null 은 NPE")
    void generatePathsNull() {
        assertThrows(NullPointerException.class, () -> FanChartCalculator.generatePaths(null));
    }

    @Test
    @DisplayName("기준선: 드리프트 0 이므로 전 구간이 현재 환율이고 길이는 horizon+1")
    void generateBaseLine() {
        List<Double> baseline = FanChartCalculator.generateBaseLine(1382.40, 3);

        assertEquals(4, baseline.size());
        assertTrue(baseline.stream().allMatch(v -> v == 1382.40));
    }

    @Test
    @DisplayName("기준선: 지평이 0 이하면 예외")
    void generateBaseLineInvalidHorizon() {
        assertThrows(IllegalArgumentException.class,
                () -> FanChartCalculator.generateBaseLine(1382.40, 0));
    }

    @Test
    @DisplayName("해석적 구간: 명세 fixture(1382.40 · 변동성 0.061 · 30일)의 재현값")
    void analyticIntervalFixture() {
        FanChartCalculator.PathPoint point =
                FanChartCalculator.analyticInterval(1382.40, 0.061, 30);

        // sigma_30d = 0.061 * sqrt(30/252) = 0.0210469...
        // lo = 1382.40 * exp(-1.2815516 * sigma) , hi = 1382.40 * exp(+1.2815516 * sigma)
        assertEquals(1345.61, point.p80Lo(), 0.01);
        assertEquals(1420.19, point.p80Hi(), 0.01);
        assertTrue(point.p50Lo() > point.p80Lo());
        assertTrue(point.p50Hi() < point.p80Hi());
    }

    @Test
    @DisplayName("해석적 구간: 변동성 0 이면 구간이 기준값 한 점으로 붕괴한다")
    void analyticIntervalZeroVol() {
        FanChartCalculator.PathPoint point = FanChartCalculator.analyticInterval(1000.0, 0.0, 30);

        assertEquals(1000.0, point.p50Lo(), 1e-9);
        assertEquals(1000.0, point.p50Hi(), 1e-9);
        assertEquals(1000.0, point.p80Lo(), 1e-9);
        assertEquals(1000.0, point.p80Hi(), 1e-9);
    }

    @Test
    @DisplayName("해석적 구간: 입력이 범위를 벗어나면 예외")
    void analyticIntervalInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> FanChartCalculator.analyticInterval(0.0, 0.061, 30));
        assertThrows(IllegalArgumentException.class,
                () -> FanChartCalculator.analyticInterval(1000.0, -0.01, 30));
        assertThrows(IllegalArgumentException.class,
                () -> FanChartCalculator.analyticInterval(1000.0, 0.061, 0));
    }
}
