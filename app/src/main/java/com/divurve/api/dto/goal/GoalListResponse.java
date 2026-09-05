package com.divurve.api.dto.goal;

import java.util.List;

/** 목표 목록 응답 (GET /goals). */
public record GoalListResponse(List<GoalResponse> goals) {
}
