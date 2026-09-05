package com.divurve.api.dto.xray;

import java.util.List;
import java.util.Map;

/**
 * 통화 노출·외화 비중·민감도 응답 (GET /xray, 명세 3.3).
 */
public record XrayResponse(
        long totalAssetKrw,
        long fxAssetKrw,
        double fxRatio,
        List<Exposure> exposure,
        Concentration concentration,
        Sensitivity sensitivity1pct,
        long dayChangeKrw,
        List<UpcomingOutflow> upcomingOutflows) {

    /** 통화별 노출 금액과 비중. */
    public record Exposure(String currencyCode, long krw, double share) {
    }

    /** 집중도 진단. */
    public record Concentration(String topCurrency, double share, double threshold, String status) {
    }

    /** 1퍼센트 변동 민감도. */
    public record Sensitivity(long totalKrw, Map<String, Long> byCurrency) {
    }

    /** 예정 외화 지출. */
    public record UpcomingOutflow(
            String goalId,
            String date,
            String currencyCode,
            double amount,
            boolean hasPlan) {
    }
}
