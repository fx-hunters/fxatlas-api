package com.divurve.api.dto.plan;

import java.util.List;

/** 계획 버전 이력 응답 (GET /goals/{id}/plans). */
public record PlanVersionListResponse(List<Version> versions) {

    /** 계획 버전 요약. */
    public record Version(
            String id,
            int version,
            boolean isActive,
            String reason,
            String createdAt) {
    }
}
