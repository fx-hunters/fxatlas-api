package com.divurve.api.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;

/** 원화 자산 수정 요청 (API 명세 v2 §3 {@code PUT /krw-assets/:id}, FR-XR-07). */
@Schema(description = "원화 자산 수정")
public record KrwAssetUpdateRequest(
        @Schema(description = "자산 종류", example = "deposit",
                allowableValues = {"cash", "deposit", "domestic_equity", "other"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,

        @Schema(description = "사용자가 붙인 이름표", example = "적금", nullable = true)
        String label,

        @Schema(description = "금액(원)", example = "50000000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long amountKrw) {
}
