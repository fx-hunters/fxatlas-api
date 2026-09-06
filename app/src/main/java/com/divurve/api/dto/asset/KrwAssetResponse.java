package com.divurve.api.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;

/** 원화 자산 응답 (API 명세 v2 §3 {@code GET /krw-assets}). */
@Schema(description = "원화 자산")
public record KrwAssetResponse(
        @Schema(description = "자산 ID", example = "0f6a2c7e-2f1e-4d1a-9a1b-5b6c7d8e9f01")
        String id,

        @Schema(description = "자산 종류", example = "cash",
                allowableValues = {"cash", "deposit", "domestic_equity", "other"})
        String kind,

        @Schema(description = "사용자가 붙인 이름표", example = "주거래 통장", nullable = true)
        String label,

        @Schema(description = "금액(원)", example = "43680000")
        long amountKrw) {
}
