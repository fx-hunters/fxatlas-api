package com.divurve.engine.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 팬차트(Fan Chart) 구간 계산 (FR-FC-02).
 *
 * <p>지오메트릭 브라우니안 모션(GBM) 기반 시뮬레이션에서:
 * - 기준선: 드리프트 0인 경우의 중앙값 (계산용 유일 기준)
 * - 경로: 50% 구간 (분위수 0.25~0.75), 80% 구간 (분위수 0.1~0.9)
 * 과거 데이터는 별도로 전달된다 (팬차트는 미래만 포함).
 *
 * <p>NFR-DT-01: 모든 경로 포인트에는 기준 시각과 함께 제공한다.
 */
public class FanChartCalculator {

    private FanChartCalculator() {
    }

    /**
     * 팬차트 경로점들을 생성한다.
     *
     * @param simulationPaths 시뮬레이션된 환율 경로들 (행=경로 수, 열=시점)
     * @return 각 시점별 50%/80% 구간 점들
     */
    public static List<PathPoint> generatePaths(List<List<Double>> simulationPaths) {
        Objects.requireNonNull(simulationPaths, "simulationPaths must not be null");
        if (simulationPaths.isEmpty()) {
            return new ArrayList<>();
        }

        int timeSteps = simulationPaths.get(0).size();
        List<PathPoint> points = new ArrayList<>();

        for (int t = 0; t < timeSteps; t++) {
            List<Double> valuesAtT = new ArrayList<>();
            for (List<Double> path : simulationPaths) {
                valuesAtT.add(path.get(t));
            }

            double p50Lo = percentile(valuesAtT, 0.25);
            double p50Hi = percentile(valuesAtT, 0.75);
            double p80Lo = percentile(valuesAtT, 0.10);
            double p80Hi = percentile(valuesAtT, 0.90);

            points.add(new PathPoint(p50Lo, p50Hi, p80Lo, p80Hi));
        }

        return points;
    }

    /**
     * 기준선 계산 (드리프트 0인 경우의 중앙값 경로).
     *
     * <p>드리프트가 0인 GBM에서 로그 정규분포의 중위수는 현재값과 같다.
     * 따라서 기준선은 모든 시점에서 initialRate이다.
     *
     * @param initialRate 현재 환율
     * @param horizonDays 미래 지평 (일수)
     * @return 각 일별 기준선 값 (모두 initialRate)
     */
    public static List<Double> generateBaseLine(double initialRate, int horizonDays) {
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("horizonDays must be positive");
        }
        List<Double> baseline = new ArrayList<>();
        for (int i = 0; i <= horizonDays; i++) {
            baseline.add(initialRate);
        }
        return baseline;
    }

    /**
     * 80% 구간 상단값 (기준 대비 상승률로 표현).
     *
     * @param baseRate 드리프트 0 기준선
     * @param p80Hi 80% 구간 상단
     * @return (p80Hi - baseRate) / baseRate (예: 0.05 = 5% 상승)
     */
    public static double interval80WidthHighPct(double baseRate, double p80Hi) {
        return (p80Hi - baseRate) / baseRate;
    }

    /**
     * 80% 구간 하단값 (기준 대비 하락률로 표현).
     *
     * @param baseRate 드리프트 0 기준선
     * @param p80Lo 80% 구간 하단
     * @return (baseRate - p80Lo) / baseRate (예: 0.05 = 5% 하락)
     */
    public static double interval80WidthLowPct(double baseRate, double p80Lo) {
        return (baseRate - p80Lo) / baseRate;
    }

    /**
     * 3년 평균 대비 80% 구간 폭 비율.
     *
     * @param p80Hi 80% 구간 상단
     * @param p80Lo 80% 구간 하단
     * @param threeYearAvg 3년 평균 환율
     * @return 폭 / 3년 평균
     */
    public static double interval80VsThreeYearAvg(double p80Hi, double p80Lo, double threeYearAvg) {
        double width = p80Hi - p80Lo;
        return width / threeYearAvg;
    }

    private static double percentile(List<Double> values, double p) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);

        double index = p * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper) {
            return sorted.get(lower);
        }

        double fraction = index - lower;
        return sorted.get(lower) * (1 - fraction) + sorted.get(upper) * fraction;
    }

    /**
     * 시점별 팬차트 경로 포인트.
     */
    public record PathPoint(double p50Lo, double p50Hi, double p80Lo, double p80Hi) {
    }
}
