package com.divurve.engine.forecast;

import java.util.List;
import java.util.Objects;

/**
 * 변동성 지표 계산 — 실현변동성, 5년 백분위, 국면 라벨 (FR-FC-03/04).
 *
 * <p>NFR-DT-01: 모든 출력에 기준 시각이 함께 제공된다 (입력 dailyReturns의 as-of 날짜).
 * 미래 데이터 누출 방지: 계산 기준이 되는 window end date는 항상 과거여야 한다(롤링 워크포워드).
 */
public class VolatilityCalculator {

    private static final int REALIZED_30D_WINDOW = 30;
    private static final int PERCENTILE_5Y_WINDOW = 252 * 5; // 5년 영업일
    private static final int REGIME_SHORT_WINDOW = 30;
    private static final int REGIME_LONG_WINDOW = 252;

    /**
     * 30일 실현변동성을 연환산율로 계산.
     *
     * @param dailyReturns 일별 수익률 (최근 최소 30개 관측값 필요)
     * @return 연환산 변동성 (예: 0.15 = 15%)
     */
    public static double calculateRealized30d(List<Double> dailyReturns) {
        Objects.requireNonNull(dailyReturns, "dailyReturns must not be null");
        if (dailyReturns.size() < REALIZED_30D_WINDOW) {
            throw new IllegalArgumentException(
                "Need at least %d daily returns, got %d".formatted(REALIZED_30D_WINDOW, dailyReturns.size())
            );
        }

        List<Double> window = dailyReturns.subList(dailyReturns.size() - REALIZED_30D_WINDOW, dailyReturns.size());
        double variance = calculateVariance(window);
        return Math.sqrt(variance * 252); // 연환산
    }

    /**
     * 5년 히스토리 내 실현변동성 상대 백분위 (0~100).
     *
     * <p>롤링 30일 변동성을 5년 윈도우에서 계산하고, 현재값의 백분위를 반환한다.
     * 예: 백분위 75 = 5년 평균보다 높음.
     *
     * @param dailyReturns 일별 수익률 (최소 5년+30일 관측값)
     * @return 백분위 (0~100)
     */
    public static int calculatePercentile5y(List<Double> dailyReturns) {
        Objects.requireNonNull(dailyReturns, "dailyReturns must not be null");
        if (dailyReturns.size() < PERCENTILE_5Y_WINDOW + REALIZED_30D_WINDOW) {
            throw new IllegalArgumentException(
                "Need at least %d daily returns for 5y percentile, got %d"
                    .formatted(PERCENTILE_5Y_WINDOW + REALIZED_30D_WINDOW, dailyReturns.size())
            );
        }

        List<Double> fiveYearWindow = dailyReturns.subList(
            Math.max(0, dailyReturns.size() - PERCENTILE_5Y_WINDOW - REALIZED_30D_WINDOW),
            dailyReturns.size()
        );

        List<Double> rollingVols = new java.util.ArrayList<>();
        for (int i = REALIZED_30D_WINDOW; i <= fiveYearWindow.size(); i++) {
            List<Double> sub = fiveYearWindow.subList(i - REALIZED_30D_WINDOW, i);
            double vol = Math.sqrt(calculateVariance(sub) * 252);
            rollingVols.add(vol);
        }

        if (rollingVols.isEmpty()) {
            return 50;
        }

        double currentVol = calculateRealized30d(dailyReturns);
        rollingVols.sort(null);

        int count = 0;
        for (Double vol : rollingVols) {
            if (vol < currentVol) {
                count++;
            }
        }

        return Math.round((100f * count) / rollingVols.size());
    }

    /**
     * 변동성 국면 라벨 — "Low", "Normal", "High".
     *
     * @param percentile5y 5년 백분위 (calculatePercentile5y 결과)
     * @return 국면 라벨
     */
    public static String classifyRegime(int percentile5y) {
        if (percentile5y < 33) {
            return "Low";
        } else if (percentile5y < 67) {
            return "Normal";
        } else {
            return "High";
        }
    }

    private static double calculateVariance(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        return sumSquaredDiff / values.size();
    }
}
