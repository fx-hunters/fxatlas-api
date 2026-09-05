package com.divurve.api.dto.fit;

import java.util.List;
import java.util.Map;

/** 집중도 진단 응답 (GET /fit/concentration). */
public record ConcentrationResponse(
        Map<String, Double> exposure,
        String topCurrency,
        double topShare,
        double threshold,
        String status,
        List<String> suggestions) {
}
