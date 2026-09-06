package com.divurve.api.dto.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 보유 종목 응답 (GET /holdings).
 * 현재가·현재 원화 환산액은 진단(M1-7)이 계산하므로 여기에 포함하지 않는다.
 * 매입 환율은 출처(source)·기준일(asOf)과 함께 반환한다(NFR-DT-01).
 */
public record HoldingResponse(
        String id,
        String ticker,
        String currencyCode,
        double quantity,
        double avgPrice,
        LocalDate purchasedAt,
        BigDecimal purchaseFxRateKrw,
        String purchaseFxRateSource,
        LocalDate purchaseFxRateAsOf) {
}
