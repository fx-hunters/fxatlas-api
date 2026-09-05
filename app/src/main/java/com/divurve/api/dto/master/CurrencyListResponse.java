package com.divurve.api.dto.master;

import java.util.List;

/**
 * 지원 통화와 표시 규칙 응답 (GET /currencies).
 * 필드명은 명세 그대로 — {@code minor_units} · {@code quote_unit} · {@code usd_side} · {@code color_token}.
 */
public record CurrencyListResponse(List<Currency> currencies) {

    /** 통화별 표시 규칙. */
    public record Currency(
            String currencyCode,
            int minorUnits,
            int quoteUnit,
            String usdSide,
            String colorToken) {
    }
}
