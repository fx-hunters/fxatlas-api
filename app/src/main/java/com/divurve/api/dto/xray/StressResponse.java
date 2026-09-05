package com.divurve.api.dto.xray;

import java.util.List;

/** 스트레스 시나리오 결과 응답 (POST /xray/stress). */
public record StressResponse(
        long totalAssetBeforeKrw,
        long totalAssetAfterKrw,
        long impactKrw,
        double impactRatio,
        List<ByCurrency> byCurrency) {

    /** 통화별 스트레스 영향. */
    public record ByCurrency(String currencyCode, double shock, long impactKrw) {
    }
}
