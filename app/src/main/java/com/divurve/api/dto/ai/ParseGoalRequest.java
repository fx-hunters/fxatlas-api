package com.divurve.api.dto.ai;

/** 자연어 목표 파싱 요청 (POST /ai/parse-goal, 명세 4장). */
public record ParseGoalRequest(String text) {
}
