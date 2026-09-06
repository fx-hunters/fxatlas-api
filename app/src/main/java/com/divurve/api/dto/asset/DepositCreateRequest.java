package com.divurve.api.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 외화 예금 추가 요청 (POST /deposits).
 *
 * <p>{@code purchasedAt} 을 넘기면 서버가 예치 시점 환율을 자동 조회한다(FR-ON-04).
 * 조회 실패 시에만 클라이언트가 {@code purchaseFxRateKrw} 로 폴백 값을 지정한다.
 * KRW 자산은 두 필드 모두 무시된다.
 *
 * <p>{@code currencyCode}·{@code amount} 는 둘 다 {@code fx_deposits} 테이블의 NOT NULL 컬럼이다.
 * {@code currencyCode} 가 없으면 {@code purchasedAt} 이 있을 때 {@code PurchaseFxRateResolver}
 * 가 {@code null.toUpperCase()} 로 NPE 를 던져 500 으로 샜다(이슈 #75) — {@code @NotBlank}/{@code @NotNull}
 * 로 막는다.
 */
public record DepositCreateRequest(
        @NotBlank(message = "통화코드는 필수입니다.") String currencyCode,
        @NotNull(message = "예금 금액은 필수입니다.") BigDecimal amount,
        LocalDate purchasedAt,
        BigDecimal purchaseFxRateKrw) {
}
