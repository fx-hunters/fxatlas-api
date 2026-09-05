package com.divurve.api.dto.asset;

/** 종목 수정 요청 (PUT /holdings/{id}). */
public record HoldingUpdateRequest(
        Double quantity,
        Double avgPrice) {
}
