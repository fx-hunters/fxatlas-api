package com.divurve.api.dto.fit;

import java.util.Map;

/**
 * 분산효과 시뮬레이션 응답 (POST /fit/simulate, 명세 3.8).
 * {@code suggestedGoal} 이 Fit → 플래너로 가는 다리다 (FR-FT-04).
 */
public record SimulateResponse(
        PortfolioVol portfolioVol,
        Map<String, Double> exposureAfter,
        double threshold,
        boolean withinThreshold,
        SuggestedGoal suggestedGoal) {

    /** 조정 전/후 포트폴리오 변동성. */
    public record PortfolioVol(double before, double after) {
    }

    /** 목표 생성 폼을 채울 제안 값. */
    public record SuggestedGoal(
            String kind,
            String purpose,
            String currencyCode,
            double targetAmount) {
    }
}
