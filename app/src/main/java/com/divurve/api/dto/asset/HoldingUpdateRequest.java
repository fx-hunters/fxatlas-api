package com.divurve.api.dto.asset;

/**
 * 종목 수정 요청 (PUT /holdings/{id}). 수량·평균단가만 수정 가능하다.
 * 매입일·매입환율은 최초 등록 시점에 고정된 근거값이므로 수정하지 않는다.
 */
public record HoldingUpdateRequest(
        double quantity,
        double avgPrice) {
}
