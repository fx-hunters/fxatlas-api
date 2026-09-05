package com.divurve.api.dto.asset;

/** 외화 예금 응답 (GET /deposits). 외화는 소수 4자리 (명세 1.4). */
public record DepositResponse(
        String id,
        String currencyCode,
        double amount,
        long valueKrw) {
}
