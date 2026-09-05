package com.divurve.api.dto.asset;

/** 외화 예금 추가 요청 (POST /deposits). */
public record DepositCreateRequest(
        String currencyCode,
        double amount) {
}
