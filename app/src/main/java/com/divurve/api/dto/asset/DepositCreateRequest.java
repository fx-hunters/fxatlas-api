package com.divurve.api.dto.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 외화 예금 추가 요청 (POST /deposits).
 *
 * <p>{@code purchasedAt} 을 넘기면 서버가 예치 시점 환율을 자동 조회한다(FR-ON-04).
 * 조회 실패 시에만 클라이언트가 {@code purchaseFxRateKrw} 로 폴백 값을 지정한다.
 * KRW 자산은 두 필드 모두 무시된다.
 */
public record DepositCreateRequest(
        String currencyCode,
        BigDecimal amount,
        LocalDate purchasedAt,
        BigDecimal purchaseFxRateKrw) {
}
