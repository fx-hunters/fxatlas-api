package com.divurve.api.dto.forecast;

import java.util.List;

/**
 * 팬차트·구간·변동성 응답 (GET /forecast, 명세 3.5).
 * 방향 확률 필드는 두지 않는다 (FR-FC-11 Won't).
 * {@code baseRate} 는 드리프트 0 기준선(계산용 유일 중앙값), {@code modelPath} 는 표시 전용.
 */
public record ForecastResponse(
        String pairCode,
        int horizonDays,
        double currentRate,
        double baseRate,
        List<History> history,
        List<PathPoint> path,
        List<ModelPoint> modelPath,
        Interval interval80,
        Volatility volatility,
        UserImpact userImpact,
        String disclaimer) {

    /** 과거 환율 점. */
    public record History(String d, double rate) {
    }

    /** 팬차트 구간 점 (p50 / p80 상하한). */
    public record PathPoint(
            String d,
            double p50Lo,
            double p50Hi,
            double p80Lo,
            double p80Hi) {
    }

    /** 모델 경로 점 (표시 전용). */
    public record ModelPoint(String d, double rate) {
    }

    /** 80퍼센트 구간 요약. */
    public record Interval(double lo, double hi, double widthPct, double vs3yAvg) {
    }

    /** 변동성 지표. */
    public record Volatility(double realized30d, int percentile5y, String regime) {
    }

    /** 사용자 자산 영향. */
    public record UserImpact(long per1pctKrw, long assetKrw) {
    }
}
