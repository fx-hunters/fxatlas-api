package com.divurve.api.dto.plan;

import com.divurve.domain.plan.entity.Plan;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 계획 버전 이력 (플래너 명세 §18·§23 "계획 이력").
 *
 * <p>새 버전을 적용해도 과거 버전은 지우지 않는다 — 완료된 회차 기록이 거기 남아 있고,
 * 명세 §21-11 은 그 기록의 보존을 요구한다.
 *
 * @param versions 버전 목록. 최신이 먼저다
 */
@Schema(description = "계획 버전 이력")
public record PlanVersionListResponse(List<Version> versions) {

    /** 저장된 계획 목록을 버전 이력으로 바꾼다. */
    public static PlanVersionListResponse from(List<Plan> plans) {
        return new PlanVersionListResponse(plans.stream().map(Version::from).toList());
    }

    /**
     * 계획 버전 하나.
     *
     * @param planId       계획 ID
     * @param version      버전 번호
     * @param status       계획 상태 (명세 §13.1)
     * @param reason       이 버전이 만들어진 사유 (명세 §18 "변경 이유 코드")
     * @param planEndDate  계획 종료일
     * @param supersededBy 이 계획을 대체한 계획 ID. 없으면 {@code null}
     * @param createdAt    생성 시각
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Version(
            String planId,
            int version,
            String status,
            String reason,
            LocalDate planEndDate,
            String supersededBy,
            Instant createdAt) {

        static Version from(Plan plan) {
            return new Version(
                    plan.getId().toString(),
                    plan.getVersion(),
                    plan.getStatus(),
                    plan.getReason(),
                    plan.getPlanEndDate(),
                    plan.getSupersededBy() == null ? null : plan.getSupersededBy().toString(),
                    plan.getCreatedAt());
        }
    }
}
