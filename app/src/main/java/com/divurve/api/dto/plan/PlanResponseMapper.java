package com.divurve.api.dto.plan;

import static java.util.Objects.requireNonNull;

import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan/PlanStep 엔티티를 DTO로 변환하는 매퍼.
 * Entity를 API 응답으로 노출하지 않기 위해 전용 매퍼를 사용한다.
 */
public class PlanResponseMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private PlanResponseMapper() {
    }

    /**
     * Plan 엔티티와 회차 목록을 PlanResponse DTO로 변환한다.
     *
     * @param plan  계획 엔티티
     * @param steps 회차 목록
     * @return 응답 DTO
     */
    public static PlanResponse toPlanResponse(Plan plan, List<PlanStep> steps) {
        requireNonNull(plan, "plan");
        requireNonNull(steps, "steps");

        return new PlanResponse(
                plan.getId().toString(),
                plan.getGoal().getId().toString(),
                plan.getVersion(),
                plan.isActive(),
                plan.getReason(),
                plan.getSafeRatio(),
                plan.getSplitCount(),
                plan.getOpportunityAmount(),
                plan.getOpportunityTriggerRate(),
                steps.stream()
                        .map(PlanResponseMapper::toStepDto)
                        .collect(Collectors.toList()));
    }

    /**
     * PlanStep 엔티티를 Step DTO로 변환한다.
     */
    private static PlanResponse.Step toStepDto(PlanStep step) {
        return new PlanResponse.Step(
                step.getSeq(),
                step.getScheduledDate() != null
                        ? step.getScheduledDate().format(DATE_FORMATTER)
                        : null,
                step.getAmount(),
                step.getExecutedAmount(),
                step.getStatus());
    }

    /**
     * Plan 엔티티를 PlanVersionListResponse.Version으로 변환한다.
     */
    public static PlanVersionListResponse.Version toVersionDto(Plan plan) {
        requireNonNull(plan, "plan");

        return new PlanVersionListResponse.Version(
                plan.getId().toString(),
                plan.getVersion(),
                plan.isActive(),
                plan.getReason(),
                plan.getCreatedAt().toString());
    }

    /**
     * Plan 엔티티와 회차 목록을 ActivePlanResponse로 변환한다.
     */
    public static ActivePlanResponse toActivePlanResponse(Plan plan, List<PlanStep> steps) {
        requireNonNull(plan, "plan");
        requireNonNull(steps, "steps");

        return new ActivePlanResponse(
                plan.getId().toString(),
                plan.getGoal().getId().toString(),
                plan.getVersion(),
                plan.isActive(),
                plan.getReason(),
                plan.getSafeRatio(),
                plan.getSplitCount(),
                plan.getOpportunityAmount(),
                plan.getOpportunityTriggerRate(),
                steps.stream()
                        .map(PlanResponseMapper::toStepDto)
                        .collect(Collectors.toList()));
    }
}
