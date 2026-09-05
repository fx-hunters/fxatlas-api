package com.divurve.api.dto.master;

import java.util.List;

/**
 * 은행·통화·채널별 환전 조건 응답 (GET /banks/{bank_code}/fx-terms).
 * 필드명은 명세 그대로 — {@code list_spread} · {@code fixed_fee_krw}.
 */
public record FxTermsResponse(
        String bankCode,
        List<Term> terms) {

    /** 통화·채널별 조건. */
    public record Term(
            String currencyCode,
            String channel,
            double listSpread,
            long fixedFeeKrw) {
    }
}
