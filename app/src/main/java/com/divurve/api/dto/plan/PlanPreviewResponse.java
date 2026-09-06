package com.divurve.api.dto.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 계획 미리보기 응답 (POST /plans/preview, 명세 3.1). 제품의 심장 — 슬라이더 이동마다 호출되며 저장하지 않는다.
 * steps 는 전부 안전 버킷이며 회차에 bucket 필드를 두지 않는다. opportunity 는 단일 대기 물량.
 *
 * <p>{@code worst5Rate} 는 API 명세 v2 에 아직 정의되지 않은 필드다(Route 계산은 §6.3 기준 미확정).
 * 전역 SNAKE_CASE 전략이 숫자 앞에 밑줄을 넣지 않으므로, 네이밍 규칙 통일 문서 6장의 숫자 경계
 * 규약({@code [a-z]_?\d})에 맞춰 {@code worst_5_rate} 로 {@link JsonProperty} 를 고정한다(이슈 #60).
 */
public record PlanPreviewResponse(
        Goal goal,
        double unfunded,
        int weeks,
        double sigmaHorizon,
        Buckets buckets,
        Split split,
        List<Step> steps,
        Opportunity opportunity,
        Metrics metrics,
        List<Comparison> comparison,
        Concentration concentration,
        List<Warning> warnings) {

    /** 목표 요약. */
    public record Goal(String kind, String purpose, String currencyCode) {
    }

    /** 안전/기회 버킷 분리. floor 는 클라이언트 슬라이더의 min. */
    public record Buckets(double safe, double opportunity, double safeRatio, double floor) {
    }

    /** 분할 정보. gFactor 는 분산 감소 계수 g(N)(검증/디버깅용). nextStepDelta 는 슬라이더 힌트용. */
    public record Split(
            int count,
            int intervalDays,
            double gFactor,
            NextStepDelta nextStepDelta) {

        /** 분할을 1회 늘렸을 때의 이득과 비용. */
        public record NextStepDelta(double sigmaGain, long feeIncreaseKrw) {
        }
    }

    /** 회차 (전부 안전 버킷). */
    public record Step(
            int seq,
            String scheduledDate,
            double amount,
            long krwEstimate,
            double executedAmount,
            String status) {
    }

    /** 기회 버킷 단일 대기 물량. 미실행 시 최종 안전 환전일에 안전 버킷으로 편입. */
    public record Opportunity(
            double amount,
            double triggerRate,
            String finalSafeDate,
            String note) {
    }

    /** 핵심 지표. hero 는 클라이언트가 크게 표시할 값 결정 (FR-RT-15). */
    public record Metrics(
            String hero,
            double entrySigma,
            double entrySigmaOnce,
            double achieveProb,
            double achieveProbOnce,
            @JsonProperty("worst_5_rate") double worst5Rate,
            Fee fee) {

        /** 환전 비용. */
        public record Fee(long spreadKrw, long fixedKrw, long totalKrw) {
        }
    }

    /** 전략 비교 한 행. avg_rate 는 세 전략이 거의 같아야 정상. */
    public record Comparison(
            String strategy,
            Integer splitCount,
            double avgRate,
            @JsonProperty("worst_5_rate") double worst5Rate,
            long feeKrw,
            double achieveProb) {
    }

    /** 집중도 변화. verdict 는 worsens / improves / neutral. */
    public record Concentration(
            Map<String, Double> before,
            Map<String, Double> after,
            double threshold,
            String verdict) {
    }

    /** 경고. */
    public record Warning(String code, String message) {
    }
}
