package com.divurve.api.dto.me;

import java.util.List;

/**
 * 투자성향 조회 응답 (GET /me/risk-profile). 현재 성향과 진단 응답 내역을 담는다.
 */
public record RiskProfileResponse(
        String riskType,
        int score,
        List<Answer> answers) {

    /** 성향 진단 문항별 응답 내역. */
    public record Answer(String questionCode, int choice) {
    }
}
