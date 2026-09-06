package com.divurve.api.dto.asset;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 종목 추가 요청 (POST /holdings).
 *
 * <p>{@code purchasedAt} 을 넘기면 서버가 매입 시점 환율을 자동 조회한다(FR-ON-04).
 * 조회 실패 시에만 클라이언트가 {@code purchaseFxRateKrw} 로 폴백 값을 지정한다.
 * KRW 자산은 두 필드 모두 무시된다.
 *
 * <p>{@code ticker}·{@code currencyCode} 는 둘 다 {@code holdings} 테이블의 NOT NULL 컬럼이다.
 * {@code currencyCode} 가 없으면 {@code purchasedAt} 이 있을 때 {@code PurchaseFxRateResolver}
 * 가 {@code null.toUpperCase()} 로 NPE 를 던져 500 으로 샜다(이슈 #75) — {@code @NotBlank} 로 막는다.
 */
public record HoldingCreateRequest(
        @NotBlank(message = "종목 코드는 필수입니다.") String ticker,
        @NotBlank(message = "통화코드는 필수입니다.") String currencyCode,
        double quantity,
        double avgPrice,
        LocalDate purchasedAt,
        BigDecimal purchaseFxRateKrw) {
}
