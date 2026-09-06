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

    /** 연 영업일 수 — {@link GbmSimulator} 의 {@code dt = 1/252} 와 같은 환산 기준. */
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    /** 표준정규 50퍼센트 구간(0.25~0.75)의 z 값. */
    private static final double Z_50 = 0.6744897501960817;

    /** 표준정규 80퍼센트 구간(0.10~0.90)의 z 값. */
    private static final double Z_80 = 1.2815515655446004;

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
     * 드리프트 0 로그정규 구간의 해석적 경계 (몬테카를로 없이).
     *
     * <p>{@link GbmSimulator} 와 같은 모형을 닫힌 형태로 푼 것이다 — 드리프트 0 이므로 중앙값은
     * {@code baseRate} 이고, 지평 {@code horizonDays} 의 로그수익률 표준편차는
     * {@code annualVol × sqrt(horizonDays / 252)} 다({@link GbmSimulator} 와 동일한 영업일 환산).
     * 경계는 {@code baseRate × exp(±z × σ_h)}.
     *
     * <p>모델 성적표(FR-FC-11)의 워크포워드 검증처럼 <b>같은 입력에 같은 구간</b>이 나와야 하는 곳에서 쓴다.
     * 시뮬레이션은 경로 수와 시드에 따라 구간이 흔들려 성적표 수치가 재현되지 않는다.
     *
     * @param baseRate    기준 환율 (드리프트 0 중앙값)
     * @param annualVol   연환산 변동성 (예 {@code 0.061})
     * @param horizonDays 미래 지평 (영업일)
     * @return 50퍼센트·80퍼센트 구간 경계
     * @throws IllegalArgumentException baseRate 가 0 이하, annualVol 이 음수, horizonDays 가 0 이하인 경우
     */
    public static PathPoint analyticInterval(double baseRate, double annualVol, int horizonDays) {
        if (baseRate <= 0) {
            throw new IllegalArgumentException("baseRate must be positive");
        }
        if (annualVol < 0) {
            throw new IllegalArgumentException("annualVol must not be negative");
        }
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("horizonDays must be positive");
        }

        double sigmaHorizon = annualVol * Math.sqrt((double) horizonDays / TRADING_DAYS_PER_YEAR);
        return new PathPoint(
            baseRate * Math.exp(-Z_50 * sigmaHorizon),
            baseRate * Math.exp(Z_50 * sigmaHorizon),
            baseRate * Math.exp(-Z_80 * sigmaHorizon),
            baseRate * Math.exp(Z_80 * sigmaHorizon)
        );
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
