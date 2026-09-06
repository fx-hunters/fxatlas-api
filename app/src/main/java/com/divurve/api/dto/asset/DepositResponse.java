package com.divurve.api.dto.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 외화 예금 응답 (GET /deposits). 외화는 소수 4자리 (명세 1.4).
 * 현재 원화 환산액은 진단(M1-7)이 계산하므로 여기에 포함하지 않는다.
 * 매입 환율은 출처(source)·기준일(asOf)과 함께 반환한다(NFR-DT-01).
 */
public record DepositResponse(
        String id,
        String currencyCode,
        BigDecimal amount,
        LocalDate purchasedAt,
        BigDecimal purchaseFxRateKrw,
        String purchaseFxRateSource,
        LocalDate purchaseFxRateAsOf) {
}
