package com.divurve.api.dto.asset;

/** 보유 종목 응답 (GET /holdings). */
public record HoldingResponse(
        String id,
        String ticker,
        String currencyCode,
        double quantity,
        double avgPrice,
        double currentPrice,
        long valueKrw) {
}
