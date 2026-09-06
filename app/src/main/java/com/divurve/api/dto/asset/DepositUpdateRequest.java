package com.divurve.api.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 외화 예금 수정 요청 (API 명세 v2 §3 {@code PUT /deposits/:id}, FR-XR-07).
 * 잔액만 바꾼다 — 예치 시점 환율 근거(FR-ON-04)는 유지된다.
 */
@Schema(description = "외화 예금 수정")
public record DepositUpdateRequest(
        @Schema(description = "예금 잔액. 외화는 소수 4자리(명세 §1.4)", example = "5000.0000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount) {
}
