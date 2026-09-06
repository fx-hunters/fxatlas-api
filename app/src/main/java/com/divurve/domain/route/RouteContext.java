package com.divurve.domain.route;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * RouteContext — Route 가 소비할 <b>데이터 계약</b> (요구사항 v2 §3 용어, FR-RT-01, API 명세 v2 §6.1).
 *
 * <p>진단 · 전망 · 자산 · 스트레스 결과를 <b>모아서 전달만</b> 한다. 이 레코드도, 이것을 만드는
 * {@link RouteContextService} 도 <b>어떤 계획도 계산하지 않는다</b> — 계산 규칙 자체가 요구사항 v2
 * §4.12 에서 미확정이기 때문이다.
 *
 * <p>🔒 <b>{@code modelPath} 와 {@code forecastFactors} 는 의도적으로 제외한다.</b>
 * FR-FC-12 는 방향 전망(모델 경로·요인 분해)을 Route 계산의 입력으로 전달하지 않는다고 규정한다.
 * ERD 가 {@code forecasts.base_rate}(L1)와 {@code model_path}(L2)를 컬럼으로 분리한 것과 같은 원칙이며,
 * API 명세 v2 §6.1 은 이 금지를 <b>계약 수준에서</b> 강제한다. 두 필드를 여기에 추가하면
 * 그 순간 Route 가 방향 전망을 계산 입력으로 받을 수 있게 되므로 <b>추가하지 말 것</b>.
 *
 * <p>현재 값은 전부 비어 있다(P 단계). 값 채우기는 X-Ray · Forecast · RiskProfile · Stress 서비스가
 * 확정된 뒤의 후속 단계이며, 이번 단계는 <b>필드 구조만</b> 고정한다.
 *
 * @param asOf      데이터 기준 시각(UTC) — FR-CM-01
 * @param diagnosis 위험성향 진단 요약
 * @param portfolio 자산·외화 비중 요약
 * @param forecast  기준 환율 요약 ({@code model_path}·{@code forecast_factors} 제외)
 * @param stress    최근 스트레스 테스트 결과 요약
 * @param regime    시장 국면 {@code calm/normal/elevated/stress} — FR-SF-02
 */
public record RouteContext(
        Instant asOf,
        Diagnosis diagnosis,
        Portfolio portfolio,
        Forecast forecast,
        Stress stress,
        String regime) {

    /**
     * 값이 비어 있는 RouteContext. 기준 시각만 실제 값이다.
     *
     * @param asOf 데이터 기준 시각
     */
    public static RouteContext empty(Instant asOf) {
        return new RouteContext(
                asOf,
                Diagnosis.empty(),
                Portfolio.empty(),
                Forecast.empty(),
                Stress.empty(),
                null);
    }

    /**
     * 진단 요약 (FR-DG 계열 결과의 전달용 사본).
     *
     * @param status                 진단 진행 상태 {@code none/simple_done/detailed_done}
     * @param grade                  성향 등급 {@code conservative/balanced/aggressive}
     * @param score                  성향 점수
     * @param concentrationThreshold 참고 기준선 (Fit 기준선, 명세 §8 미결정)
     */
    public record Diagnosis(
            String status,
            String grade,
            Integer score,
            Double concentrationThreshold) {

        public static Diagnosis empty() {
            return new Diagnosis(null, null, null, null);
        }
    }

    /**
     * 자산 요약.
     *
     * @param totalAssetKrw 총자산(원)
     * @param fxAssetKrw    외화자산(원 환산)
     * @param fxRatio       외화 비중
     * @param exposure      통화별 노출 비중. 값 미확정 단계에서는 빈 맵이다
     */
    public record Portfolio(
            Long totalAssetKrw,
            Long fxAssetKrw,
            Double fxRatio,
            Map<String, Double> exposure) {

        public Portfolio {
            exposure = exposure == null ? Map.of() : Map.copyOf(exposure);
        }

        public static Portfolio empty() {
            return new Portfolio(null, null, null, Map.of());
        }
    }

    /**
     * 기준 환율 요약. <b>방향 전망(모델 경로·요인)은 담지 않는다</b> — FR-FC-12.
     *
     * @param pairCode   통화쌍 코드
     * @param baseRate   기준 환율(L1)
     * @param interval80 80% 구간
     * @param vol30d     30일 변동성
     * @param baseDate   기준일
     */
    public record Forecast(
            String pairCode,
            Double baseRate,
            Interval interval80,
            Double vol30d,
            LocalDate baseDate) {

        public static Forecast empty() {
            return new Forecast(null, null, Interval.empty(), null, null);
        }

        /**
         * 구간 하단·상단.
         *
         * @param lo 하단
         * @param hi 상단
         */
        public record Interval(Double lo, Double hi) {

            public static Interval empty() {
                return new Interval(null, null);
            }
        }
    }

    /**
     * 스트레스 테스트 요약.
     *
     * @param lastRunId       마지막 실행 ID
     * @param totalEffectKrw  총 영향액(원)
     */
    public record Stress(String lastRunId, Long totalEffectKrw) {

        public static Stress empty() {
            return new Stress(null, null);
        }
    }
}
