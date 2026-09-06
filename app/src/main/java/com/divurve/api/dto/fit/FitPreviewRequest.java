package com.divurve.api.dto.fit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 통화 비중 가정 요청 (API 명세 v2 §5.6 {@code POST /fit/preview}).
 * 저장하지 않는다 — 가정일 뿐이다(FR-FT-03).
 */
@Schema(description = "통화 비중 가정")
public record FitPreviewRequest(
        @Schema(description = "가정을 적용할 통화", example = "JPY", requiredMode =
                Schema.RequiredMode.REQUIRED)
        String currencyCode,

        @Schema(description = "비중 변화량 (0.10 은 10%p 상향). 나머지 통화는 비례 재배분된다",
                example = "0.10", requiredMode = Schema.RequiredMode.REQUIRED)
        double deltaShare) {
}
