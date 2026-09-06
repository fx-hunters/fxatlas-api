package com.divurve.api.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 원화 자산 등록 요청 (API 명세 v2 §3 {@code POST /krw-assets}, FR-XR-07).
 * 원화 자산은 <b>외화 비중의 분모</b>다 — v1 에는 이 입력 경로가 없어 {@code fx_ratio} 가 항상 1.0 이었다.
 */
@Schema(description = "원화 자산 등록")
public record KrwAssetCreateRequest(
        @Schema(description = "자산 종류 (ERD krw_asset_kind)", example = "cash",
                allowableValues = {"cash", "deposit", "domestic_equity", "other"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,

        @Schema(description = "사용자가 붙인 이름표", example = "주거래 통장", nullable = true)
        String label,

        @Schema(description = "금액(원). 원화는 정수다", example = "43680000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long amountKrw) {
}
