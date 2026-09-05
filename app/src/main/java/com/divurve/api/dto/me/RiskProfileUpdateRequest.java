package com.divurve.api.dto.me;

import java.util.List;

/** 성향 재진단 요청 (PUT /me/risk-profile). 문항 응답 목록을 제출한다. */
public record RiskProfileUpdateRequest(List<Answer> answers) {

    /** 문항별 선택. */
    public record Answer(String questionCode, int choice) {
    }
}
