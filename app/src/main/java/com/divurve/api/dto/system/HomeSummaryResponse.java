package com.divurve.api.dto.system;

/**
 * 홈 3블록 통합 조회 응답 (GET /home/summary).
 * 진단(xray)·환율 범위(forecast)·목표 계획(plan) 요약을 한 번에 내려준다.
 */
public record HomeSummaryResponse(
        XraySummary xray,
        ForecastSummary forecast,
        PlanSummary plan) {

    /** 진단 요약 블록. */
    public record XraySummary(long totalAssetKrw, double fxRatio, String concentrationStatus) {
    }

    /** 환율 범위 요약 블록. */
    public record ForecastSummary(String pairCode, double currentRate, String regime) {
    }

    /** 목표 계획 요약 블록. */
    public record PlanSummary(String goalId, int nextStepSeq, String nextStepDate) {
    }
}
