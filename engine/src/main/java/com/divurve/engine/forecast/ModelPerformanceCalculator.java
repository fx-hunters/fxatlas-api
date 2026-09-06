package com.divurve.engine.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 모델 성적 지표 계산 (FR-FC-11, API 명세 v2 §5.8).
 *
 * <p>모델 투명성: 방향 적중률, 상대 MAE, 구간 포함률, 평균 구간 폭, 랜덤워크 대비 개선율.
 * 모든 지표는 롤링 워크포워드 검증 결과이며, 각 시점의 기준값({@code baseRates})은
 * <b>그 시점까지의 실측값</b>이라 미래 누출이 없다.
 *
 * <h2>변경 이력 (calc)</h2>
 * <ol>
 *   <li><b>방향 적중률의 i=0 편향 제거.</b> 이전 구현은 첫 시점의 기준값을
 *       {@code forecasts.get(0)} 으로 잡아 {@code pred > prevRate} 도 {@code pred < prevRate} 도
 *       성립하지 않았고, 첫 시점이 <b>항상 미적중</b>이라 적중률이 {@code 1/n} 만큼 낮게 나왔다
 *       (예: 완벽 예측 10건이 1.0 이 아니라 0.9). 이제 기준값을 인자로 받는다.</li>
 *   <li><b>MAE·구간 폭을 비율로.</b> 명세 §5.8 예시({@code mae 0.0190}, {@code avg_width 0.0580})는
 *       환율 절대값이 아니라 기준값 대비 비율이다. 절대 MAE 는 통화쌍마다 자릿수가 달라 비교가 안 된다.</li>
 *   <li><b>랜덤워크 벤치마크 정의 수정.</b> 이전 구현은 {@code initialRate} 상수를 전 구간 예측으로 써서
 *       "직전 실측값"이라는 랜덤워크 정의와 달랐다. 이제 시점별 기준값을 그대로 예측으로 쓴다.</li>
 * </ol>
 */
public class ModelPerformanceCalculator {

    private ModelPerformanceCalculator() {
    }

    /**
     * 방향 적중률 — 기준 시점 대비 방향(상승·하락·보합)이 실제와 일치한 비율.
     *
     * <p>기준값과의 비교이므로 {@code i} 에 따라 규칙이 달라지지 않는다. 보합(예측값 = 기준값)은
     * 실제도 보합일 때만 적중이다 — <b>방향을 제시하지 않는 모델은 방향 적중률이 0 에 수렴한다.</b>
     * 이 서비스의 기준 모델은 드리프트 0 이라 의도적으로 방향을 제시하지 않으며
     * (명세 §5.7 "방향 확률 필드를 두지 않는다"), 그 사실이 이 지표에 그대로 드러난다.
     *
     * @param baseRates     각 시점의 기준값 (예측을 낸 시점의 실측 환율)
     * @param forecastRates 모델이 낸 지평 끝 값
     * @param actualRates   실제 지평 끝 값
     * @return 적중률 (0~1). 입력이 비어 있으면 0
     * @throws IllegalArgumentException 세 목록의 크기가 다른 경우
     */
    public static double calculateHitRate(
            List<Double> baseRates, List<Double> forecastRates, List<Double> actualRates) {
        Objects.requireNonNull(baseRates, "baseRates must not be null");
        Objects.requireNonNull(forecastRates, "forecastRates must not be null");
        Objects.requireNonNull(actualRates, "actualRates must not be null");
        requireSameSize(baseRates.size(), forecastRates.size());
        requireSameSize(forecastRates.size(), actualRates.size());
        if (forecastRates.isEmpty()) {
            return 0.0;
        }

        int hits = 0;
        for (int i = 0; i < forecastRates.size(); i++) {
            double base = baseRates.get(i);
            int forecastDirection = Double.compare(forecastRates.get(i), base);
            int actualDirection = Double.compare(actualRates.get(i), base);
            if (forecastDirection == actualDirection) {
                hits++;
            }
        }

        return (double) hits / forecastRates.size();
    }

    /**
     * 상대 평균 절대 오차 — {@code mean(|forecast - actual| / actual)}.
     *
     * @param forecastRates 모델이 낸 값
     * @param actualRates   실제 값 (0 이면 안 된다)
     * @return 상대 MAE (예 {@code 0.019} = 1.9퍼센트). 입력이 비어 있으면 0
     * @throws IllegalArgumentException 크기가 다르거나 실제 값에 0 이 있는 경우
     */
    public static double calculateMaeRatio(List<Double> forecastRates, List<Double> actualRates) {
        Objects.requireNonNull(forecastRates, "forecastRates must not be null");
        Objects.requireNonNull(actualRates, "actualRates must not be null");
        requireSameSize(forecastRates.size(), actualRates.size());
        if (forecastRates.isEmpty()) {
            return 0.0;
        }

        double sumRelativeError = 0.0;
        for (int i = 0; i < forecastRates.size(); i++) {
            double actual = actualRates.get(i);
            if (actual == 0.0) {
                throw new IllegalArgumentException("actualRates must not contain zero");
            }
            sumRelativeError += Math.abs(forecastRates.get(i) - actual) / Math.abs(actual);
        }

        return sumRelativeError / forecastRates.size();
    }

    /**
     * 구간 포함률 — 실제값이 80퍼센트 구간 안에 든 비율.
     *
     * <p>명세 §5.8: 이 값은 <b>반드시</b> {@link #calculateAvgWidthRatio} 와 함께 노출한다.
     * 구간을 넓히면 포함률은 얼마든지 올라가므로 폭 없이는 성적이 아니다.
     *
     * @param lowerBounds 80퍼센트 구간 하단값들
     * @param upperBounds 80퍼센트 구간 상단값들
     * @param actualRates 실제 값들
     * @return 포함률 (0~1, 이상적 0.8). 입력이 비어 있으면 0
     * @throws IllegalArgumentException 세 목록의 크기가 다른 경우
     */
    public static double calculateCoverage80(
            List<Double> lowerBounds, List<Double> upperBounds, List<Double> actualRates) {
        Objects.requireNonNull(lowerBounds, "lowerBounds must not be null");
        Objects.requireNonNull(upperBounds, "upperBounds must not be null");
        Objects.requireNonNull(actualRates, "actualRates must not be null");
        requireSameSize(lowerBounds.size(), upperBounds.size());
        requireSameSize(upperBounds.size(), actualRates.size());
        if (actualRates.isEmpty()) {
            return 0.0;
        }

        int covered = 0;
        for (int i = 0; i < actualRates.size(); i++) {
            double actual = actualRates.get(i);
            if (actual >= lowerBounds.get(i) && actual <= upperBounds.get(i)) {
                covered++;
            }
        }

        return (double) covered / actualRates.size();
    }

    /**
     * 상대 평균 구간 폭 — {@code mean((upper - lower) / base)}.
     *
     * @param lowerBounds 80퍼센트 구간 하단값들
     * @param upperBounds 80퍼센트 구간 상단값들
     * @param baseRates   각 시점의 기준값 (0 이면 안 된다)
     * @return 상대 폭 (예 {@code 0.058} = 기준값의 5.8퍼센트). 입력이 비어 있으면 0
     * @throws IllegalArgumentException 크기가 다르거나 기준값에 0 이 있는 경우
     */
    public static double calculateAvgWidthRatio(
            List<Double> lowerBounds, List<Double> upperBounds, List<Double> baseRates) {
        Objects.requireNonNull(lowerBounds, "lowerBounds must not be null");
        Objects.requireNonNull(upperBounds, "upperBounds must not be null");
        Objects.requireNonNull(baseRates, "baseRates must not be null");
        requireSameSize(lowerBounds.size(), upperBounds.size());
        requireSameSize(upperBounds.size(), baseRates.size());
        if (lowerBounds.isEmpty()) {
            return 0.0;
        }

        double sumWidthRatio = 0.0;
        for (int i = 0; i < lowerBounds.size(); i++) {
            double base = baseRates.get(i);
            if (base == 0.0) {
                throw new IllegalArgumentException("baseRates must not contain zero");
            }
            sumWidthRatio += (upperBounds.get(i) - lowerBounds.get(i)) / base;
        }

        return sumWidthRatio / lowerBounds.size();
    }

    /**
     * 랜덤워크 벤치마크 — 각 시점의 <b>직전 실측값</b>을 그대로 지평 끝 값으로 쓰는 기준 모형.
     *
     * <p>환율 예측의 표준 비교 대상이다. 드리프트 0 기준선 모델은 점예측이 이 벤치마크와 같으므로
     * MAE 가 일치하고 {@link #calculateImprovement} 가 0 이 된다 — 숨기지 않고 그대로 보여준다(명세 §5.8).
     *
     * @param baseRates   각 시점의 기준값
     * @param actualRates 실제 지평 끝 값들
     * @return 랜덤워크의 방향 적중률과 상대 MAE
     * @throws IllegalArgumentException 크기가 다르거나 실제 값에 0 이 있는 경우
     */
    public static RandomWalkMetrics calculateRandomWalkBenchmark(
            List<Double> baseRates, List<Double> actualRates) {
        Objects.requireNonNull(baseRates, "baseRates must not be null");
        Objects.requireNonNull(actualRates, "actualRates must not be null");

        List<Double> randomWalkRates = new ArrayList<>(baseRates);
        double hitRate = calculateHitRate(baseRates, randomWalkRates, actualRates);
        double mae = calculateMaeRatio(randomWalkRates, actualRates);

        return new RandomWalkMetrics(hitRate, mae);
    }

    /**
     * 랜덤워크 대비 개선율 — {@code (rwMae - modelMae) / rwMae}.
     *
     * <p>음수(악화)여도 그대로 반환한다. 명세 §5.8 이 "음수여도 그대로 보여준다"고 못박는다.
     *
     * @param modelMae 모델 상대 MAE
     * @param rwMae    랜덤워크 상대 MAE
     * @return 개선율 (예 {@code 0.1} = 10퍼센트 개선). {@code rwMae} 가 0 이면 0
     */
    public static double calculateImprovement(double modelMae, double rwMae) {
        if (rwMae == 0) {
            return 0.0;
        }
        return (rwMae - modelMae) / rwMae;
    }

    private static void requireSameSize(int left, int right) {
        if (left != right) {
            throw new IllegalArgumentException("all lists must have same size");
        }
    }

    /**
     * 랜덤워크 벤치마크 메트릭.
     *
     * @param hitRate 방향 적중률
     * @param mae     상대 MAE
     */
    public record RandomWalkMetrics(double hitRate, double mae) {
    }
}
