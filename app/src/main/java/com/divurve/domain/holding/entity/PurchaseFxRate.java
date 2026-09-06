package com.divurve.domain.holding.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 매입 시점 원화 환산 근거 값객체 (FR-ON-04, NFR-DT-01).
 *
 * <p>도메인이 외부 어댑터에서 조회한 환율이든, 사용자가 폴백으로 수동 입력한 환율이든
 * 동일하게 취급할 수 있도록 (rate, 출처, 기준일) 을 하나로 묶는다.
 * source 예: "ECOS"(자동 조회), "manual"(수동 입력).
 *
 * @param rateKrw 외화 1단위당 원화 환율 (asOf 기준)
 * @param source  출처 식별자 — 자동/수동 구분 포함
 * @param asOf    환율 기준 영업일
 */
public record PurchaseFxRate(BigDecimal rateKrw, String source, LocalDate asOf) {
}
