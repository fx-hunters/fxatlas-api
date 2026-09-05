package com.divurve.api.dto.asset;

/** 종목 추가 요청 (POST /holdings). */
public record HoldingCreateRequest(
        String ticker,
        String currencyCode,
        double quantity,
        double avgPrice) {
}
