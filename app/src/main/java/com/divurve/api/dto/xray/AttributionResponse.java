package com.divurve.api.dto.xray;

import java.util.List;

/**
 * 손익 분해 응답 (GET /xray/attribution, 명세 3.4).
 * mode 는 {@code three_way}(자산/환율/교차항 분리) 또는 {@code shapley}(교차항 절반 배분).
 */
public record AttributionResponse(
        String currencyCode,
        String mode,
        long costBasisKrw,
        long currentKrw,
        double totalReturn,
        List<Component> components,
        List<ByHolding> byHolding) {

    /** 손익 구성요소 (asset / fx / interaction / cost). */
    public record Component(String key, long krw, double contributionPp) {
    }

    /** 종목별 손익 분해. */
    public record ByHolding(
            String ticker,
            long krw,
            double localReturn,
            double fxContributionPp,
            double krwReturn) {
    }
}
