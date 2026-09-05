package com.divurve.api.dto.forecast;

import java.util.List;

/** 전망 동인 응답 (GET /forecast/factors). */
public record FactorsResponse(
        String pairCode,
        List<Factor> factors) {

    /** 개별 전망 동인과 기여 방향. */
    public record Factor(
            String key,
            String label,
            double contributionPp,
            String direction) {
    }
}
