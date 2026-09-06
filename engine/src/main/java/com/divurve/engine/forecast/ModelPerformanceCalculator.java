package com.divurve.engine.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 모델 성적 지표 계산 (FR-FC-08).
 *
 * <p>모델 투명성: 적중률, MAE, 구간 포함률, 평균 구간 폭, 랜덤워크 대비 개선율.
 * 모든 지표는 검증 데이터(테스트 셋) 기준이며, 롤링 워크포워드로 미래 누출 방지.
 */
public class ModelPerformanceCalculator {

    private ModelPerformanceCalculator() {
    }

    /**
     * 방향 적중률 (Direction Accuracy).
     *
     * <p>예측 방향(상/하)과 실제 방향이 일치하는 비율.
     * 상승: predicted > actual_start, 하락: predicted < actual_start
     *
     * @param predictions 모델 예측값들 (시점 끝값)
     * @param actuals 실제 관측값들 (시점 끝값)
     * @return 적중률 (0~1)
     */
    public static double calculateHitRate(List<Double> predictions, List<Double> actuals) {
        Objects.requireNonNull(predictions, "predictions must not be null");
        Objects.requireNonNull(actuals, "actuals must not be null");
        if (predictions.size() != actuals.size()) {
            throw new IllegalArgumentException("predictions and actuals must have same size");
        }
        if (predictions.isEmpty()) {
            return 0.0;
        }

        int hits = 0;
        for (int i = 0; i < predictions.size(); i++) {
            double pred = predictions.get(i);
            double actual = actuals.get(i);
            double prevRate = i > 0 ? actuals.get(i - 1) : predictions.get(0); // 임시: 첫 예측을 기준

            if ((pred > prevRate && actual > prevRate) || (pred < prevRate && actual < prevRate)) {
                hits++;
            }
        }

        return (double) hits / predictions.size();
    }

    /**
     * 평균 절대 오차 (Mean Absolute Error).
     *
     * @param predictions 모델 예측값들
     * @param actuals 실제 관측값들
     * @return MAE (절대 오차 평균)
     */
    public static double calculateMae(List<Double> predictions, List<Double> actuals) {
        Objects.requireNonNull(predictions, "predictions must not be null");
        Objects.requireNonNull(actuals, "actuals must not be null");
        if (predictions.size() != actuals.size()) {
            throw new IllegalArgumentException("predictions and actuals must have same size");
        }
        if (predictions.isEmpty()) {
            return 0.0;
        }

        double sumAbsError = 0.0;
        for (int i = 0; i < predictions.size(); i++) {
            sumAbsError += Math.abs(predictions.get(i) - actuals.get(i));
        }

        return sumAbsError / predictions.size();
    }

    /**
     * 구간 포함률 (Coverage) — 실제값이 80% 예측 구간에 포함되는 비율.
     *
     * @param lowerBounds 80% 구간 하단값들
     * @param upperBounds 80% 구간 상단값들
     * @param actuals 실제 관측값들
     * @return 포함률 (0~1, 이상적=0.8)
     */
    public static double calculateCoverage80(List<Double> lowerBounds, List<Double> upperBounds, List<Double> actuals) {
        Objects.requireNonNull(lowerBounds, "lowerBounds must not be null");
        Objects.requireNonNull(upperBounds, "upperBounds must not be null");
        Objects.requireNonNull(actuals, "actuals must not be null");
        if (!(lowerBounds.size() == upperBounds.size() && upperBounds.size() == actuals.size())) {
            throw new IllegalArgumentException("All lists must have same size");
        }
        if (actuals.isEmpty()) {
            return 0.0;
        }

        int covered = 0;
        for (int i = 0; i < actuals.size(); i++) {
            double actual = actuals.get(i);
            if (actual >= lowerBounds.get(i) && actual <= upperBounds.get(i)) {
                covered++;
            }
        }

        return (double) covered / actuals.size();
    }

    /**
     * 평균 구간 폭 (Average Width) — 80% 구간의 상하단 간 거리 평균.
     *
     * @param lowerBounds 80% 구간 하단값들
     * @param upperBounds 80% 구간 상단값들
     * @return 평균 폭
     */
    public static double calculateAvgWidth(List<Double> lowerBounds, List<Double> upperBounds) {
        Objects.requireNonNull(lowerBounds, "lowerBounds must not be null");
        Objects.requireNonNull(upperBounds, "upperBounds must not be null");
        if (lowerBounds.size() != upperBounds.size()) {
            throw new IllegalArgumentException("lowerBounds and upperBounds must have same size");
        }
        if (lowerBounds.isEmpty()) {
            return 0.0;
        }

        double sumWidth = 0.0;
        for (int i = 0; i < lowerBounds.size(); i++) {
            sumWidth += upperBounds.get(i) - lowerBounds.get(i);
        }

        return sumWidth / lowerBounds.size();
    }

    /**
     * 랜덤워크 벤치마크 — 단순 표류 모형(no drift, no drift).
     * 실제로는 외부에서 제공되지만, 여기서는 간단한 참고값 계산.
     *
     * @param initialRate 초기값
     * @param actuals 실제 종가들
     * @return RandomWalkMetrics
     */
    public static RandomWalkMetrics calculateRandomWalkBenchmark(double initialRate, List<Double> actuals) {
        Objects.requireNonNull(actuals, "actuals must not be null");

        List<Double> rwPredictions = new ArrayList<>();
        double currentRate = initialRate;
        for (int i = 0; i < actuals.size(); i++) {
            rwPredictions.add(currentRate);
        }

        double hitRate = calculateHitRate(rwPredictions, actuals);
        double mae = calculateMae(rwPredictions, actuals);

        return new RandomWalkMetrics(hitRate, mae);
    }

    /**
     * 모델이 랜덤워크 대비 얼마나 개선되었는지 (개선율).
     *
     * @param modelMae 모델 MAE
     * @param rwMae 랜덤워크 MAE
     * @return 개선율 (예: 0.1 = 10% 개선, 음수 = 악화)
     */
    public static double calculateImprovement(double modelMae, double rwMae) {
        if (rwMae == 0) {
            return 0.0;
        }
        return (rwMae - modelMae) / rwMae;
    }

    /**
     * 랜덤워크 벤치마크 메트릭.
     */
    public record RandomWalkMetrics(double hitRate, double mae) {
    }
}
