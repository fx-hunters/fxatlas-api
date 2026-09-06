package com.divurve.api.controller;

import static java.util.Objects.requireNonNull;

import com.divurve.api.dto.plan.ActivePlanResponse;
import com.divurve.api.dto.plan.PlanCreateRequest;
import com.divurve.api.dto.plan.PlanPreviewRequest;
import com.divurve.api.dto.plan.PlanPreviewResponse;
import com.divurve.api.dto.plan.PlanPreviewResponseMapper;
import com.divurve.api.dto.plan.PlanResponse;
import com.divurve.api.dto.plan.PlanResponseMapper;
import com.divurve.api.dto.plan.PlanVersionListResponse;
import com.divurve.api.dto.plan.StepCompleteRequest;
import com.divurve.api.dto.plan.StepCompleteResponse;
import com.divurve.api.dto.plan.StepSkipResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.divurve.domain.plan.PlanPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계획 엔드포인트 (명세 2·3.1·3.7장).
 * 계획 확정, 이력 조회, 활성 계획 조회, 회차 완료/건너뛰기를 담당한다.
 * 경로가 /plans 와 /goals/{id}/plans 를 넘나들어 base 는 /api/v1 로 둔다.
 * preview 엔드포인트는 구현됨, 나머지는 미구현.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Plan", description = "계획 미리보기·확정·회차 실행")
public class PlanController {

    private final GoalRepository goalRepository;
    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanConfirmService planConfirmService;
    private final PlanStepExecutionService planStepExecutionService;

    public PlanController(
            GoalRepository goalRepository,
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            PlanConfirmService planConfirmService,
            PlanStepExecutionService planStepExecutionService) {
        this.goalRepository = requireNonNull(goalRepository, "goalRepository");
        this.planRepository = requireNonNull(planRepository, "planRepository");
        this.planStepRepository = requireNonNull(planStepRepository, "planStepRepository");
        this.planConfirmService = requireNonNull(planConfirmService, "planConfirmService");
        this.planStepExecutionService = requireNonNull(planStepExecutionService, "planStepExecutionService");
    }

    @Operation(summary = "계획 미리보기 (저장하지 않는다)")
    @PostMapping("/plans/preview")
    public ApiResponse<PlanPreviewResponse> preview(@RequestBody PlanPreviewRequest request) {
        var previewInfo = planPreviewService.generatePreview(
                request.goalId(),
                request.weeklyBudgetKrw(),
                request.safeRatio(),
                request.splitCount()
        );

        return ApiResponse.of(planPreviewResponseMapper.toResponse(previewInfo));
    }

    @Operation(summary = "계획 확정·저장")
    @PostMapping("/goals/{id}/plans")
    public ApiResponse<PlanResponse> createPlan(
            @PathVariable String id,
            @RequestBody PlanCreateRequest request) {
        UUID goalId = UUID.fromString(id);

        Plan savedPlan = planConfirmService.confirmAndSavePlan(
                goalId,
                request.safeRatio(),
                request.splitCount(),
                0.0, // opportunityAmount는 요청에 없으므로 기본값
                0.0, // triggerRate도 기본값
                null); // 초기 생성이므로 사유 없음

        List<PlanStep> steps = planStepRepository.findByPlan_IdOrderBySeqAsc(savedPlan.getId());
        PlanResponse response = PlanResponseMapper.toPlanResponse(savedPlan, steps);

        return ApiResponse.of(response);
    }

    @Operation(summary = "계획 버전 이력")
    @GetMapping("/goals/{id}/plans")
    public ApiResponse<PlanVersionListResponse> listPlanVersions(@PathVariable String id) {
        UUID goalId = UUID.fromString(id);

        List<Plan> plans = planRepository.findByGoal_Id(goalId);
        List<PlanVersionListResponse.Version> versions = plans.stream()
                .map(PlanResponseMapper::toVersionDto)
                .collect(Collectors.toList());

        return ApiResponse.of(new PlanVersionListResponse(versions));
    }

    @Operation(summary = "활성 계획과 회차")
    @GetMapping("/goals/{id}/plans/active")
    public ApiResponse<ActivePlanResponse> getActivePlan(@PathVariable String id) {
        UUID goalId = UUID.fromString(id);

        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Active plan not found for goal: " + goalId));

        List<PlanStep> steps = planStepRepository.findByPlan_IdOrderBySeqAsc(activePlan.getId());
        ActivePlanResponse response = PlanResponseMapper.toActivePlanResponse(activePlan, steps);

        return ApiResponse.of(response);
    }

    @Operation(summary = "회차 완료 기록")
    @PostMapping("/plans/{id}/steps/{seq}/complete")
    public ApiResponse<StepCompleteResponse> completeStep(
            @PathVariable String id,
            @PathVariable int seq,
            @RequestBody StepCompleteRequest request) {
        UUID planId = UUID.fromString(id);

        PlanStep completedStep = planStepExecutionService.completeStep(
                planId,
                seq,
                request.executedAmount());

        StepCompleteResponse response = new StepCompleteResponse(
                completedStep.getSeq(),
                completedStep.getStatus(),
                completedStep.getExecutedAmount(),
                request.executedRate(),
                0.0); // remainingAmount는 나중에 계산

        return ApiResponse.of(response);
    }

    @Operation(summary = "회차 건너뛰기")
    @PostMapping("/plans/{id}/steps/{seq}/skip")
    public ApiResponse<StepSkipResponse> skipStep(
            @PathVariable String id,
            @PathVariable int seq) {
        UUID planId = UUID.fromString(id);

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        double targetAmount = plan.getGoal().getTargetAmount();

        PlanStepExecutionService.SkipResult skipResult =
                planStepExecutionService.skipStep(planId, seq, targetAmount);

        StepSkipResponse response = new StepSkipResponse(
                new StepSkipResponse.Redistributed(
                        skipResult.burdenBefore(),
                        skipResult.burdenAfter(),
                        skipResult.burdenIncreasePct()),
                new StepSkipResponse.AchieveProb(0.0, 0.0), // TODO: 실제 달성확률 계산 필요
                skipResult.consecutiveSkips(),
                skipResult.safeModeTriggered(),
                plan.getVersion()); // 현재 버전

        return ApiResponse.of(response);
    }
}
