package com.divurve.engine.forecast;

import java.util.List;
import java.util.Objects;

/**
 * 변동성 지표 계산 — 실현변동성, 5년 백분위 (FR-FC-03/04).
 *
 * <p>NFR-DT-01: 모든 출력에 기준 시각이 함께 제공된다 (입력 dailyReturns의 as-of 날짜).
 * 미래 데이터 누출 방지: 계산 기준이 되는 window end date는 항상 과거여야 한다(롤링 워크포워드).
 *
 * <h2>국면 분류는 여기 없다</h2>
 * {@code classifyRegime(int) → "Low"/"Normal"/"High"} 를 제거하고
 * {@code engine.volatility.RegimeClassifier} 로 옮겼다. ERD {@code fx_stats.regime} 는
 * {@code calm/normal/elevated/stress} 4종이고 {@code "High"} 는 그 ENUM 에 없는 값이었다
 * (API 명세 v2 §0.3). 호출처는 {@code RegimeClassifier.classify(double)} 를 쓴다.
 *
 * <h2>백분위 단위 변경 (calc)</h2>
 * {@link #calculatePercentile5y}가 <b>0~100 정수 → 0~1 비율 double</b> 로 바뀌었다.
 * ERD {@code fx_stats.vol_percentile_5y} 가 {@code NUMERIC(5,4)} 이고
 * API 명세 §1.4 가 "비율은 0과 1 사이 소수"를 규정하므로 정수 백분위는 저장·전송 단위가 아니었다.
 * 반올림도 사라졌다 — 같은 입력(5년 롤링 변동성 2,500개 중 1,801개가 현재값 미만)에 대해
 * 변경 전 {@code 72}(= round(72.04)), 변경 후 {@code 0.7204} 를 낸다.
 */
public class VolatilityCalculator {

    private VolatilityCalculator() {
        throw new UnsupportedOperationException("VolatilityCalculator is a utility class");
    }

    /** 실현변동성 롤링 윈도(영업일). 워크포워드 검증이 폴드 최소 시작점을 잡는 데 쓴다. */
    public static final int REALIZED_30D_WINDOW = 30;
    private static final int PERCENTILE_5Y_WINDOW = 252 * 5; // 5년 영업일

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
     * 5년 히스토리 내 실현변동성 상대 백분위 (0~1 비율).
     *
     * <p>롤링 30일 변동성을 5년 윈도우에서 계산하고, 현재값보다 낮은 관측치의 비율을 반환한다.
     * 예: {@code 0.72} = 5년 롤링 변동성의 72퍼센트가 현재값보다 낮음(= 상위 28퍼센트 구간).
     * 단위는 ERD {@code fx_stats.vol_percentile_5y}({@code NUMERIC(5,4)})와 같다.
     *
     * @param dailyReturns 일별 수익률 (최소 5년+30일 관측값)
     * @return 백분위 비율 (0~1)
     */
    public static double calculatePercentile5y(List<Double> dailyReturns) {
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

        double currentVol = calculateRealized30d(dailyReturns);
        rollingVols.sort(null);

        int count = 0;
        for (Double vol : rollingVols) {
            if (vol < currentVol) {
                count++;
            }
        }

        return (double) count / rollingVols.size();
    }

    private static double calculateVariance(List<Double> values) {
        Objects.requireNonNull(values, "values must not be null");
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        return sumSquaredDiff / values.size();
    }
}
