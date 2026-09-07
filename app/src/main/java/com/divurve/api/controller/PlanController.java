package com.divurve.api.controller;

import static java.util.Objects.requireNonNull;

import com.divurve.api.config.auth.CurrentUser;
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
import com.divurve.common.exception.NotFoundException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.plan.PlanAccessService;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanPreviewService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계획 엔드포인트 (API 명세 v2 §6) — <b>우선순위 P(구조만 준비)</b>.
 * 계획 미리보기, 확정, 이력 조회, 활성 계획 조회, 회차 완료/건너뛰기를 담당한다.
 * 경로가 /plans 와 /goals/{id}/plans 를 넘나들어 base 는 /api/v1 로 둔다.
 *
 * <p><b>Route 기능 플래그를 제거했다</b> (이슈 #84). 플래그는 요구사항 v2 §4.12 가 계산 규칙을
 * 미확정으로 둔 동안 확정되지 않은 수치가 API 로 새어 나가지 않게 막던 장치였고, 플래너 명세가
 * 계산 정책을 확정하면서 막을 대상이 사라졌다.
 *
 * <p>다만 <b>계약과 계산은 아직 옛 모델</b>이다 — {@code safe_ratio}·{@code split_count}·
 * {@code opportunity_*}·{@code achieve_prob} 는 플래너 명세 §23 이 산출 근거 불명으로 지목한
 * 값들이며, 새 계약으로의 교체는 이슈 #85 에서 한다.
 *
 * <p>모든 엔드포인트가 {@link PlanAccessService} 로 소유자를 먼저 검증한다 (이슈 #50).
 * 그 이전에는 <b>소유자 검증이 전혀 없어</b> {@code goalId}/{@code planId} 만 알면
 * 남의 계획을 읽고 회차를 완료·건너뛸 수 있었다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Plan", description = "계획 미리보기·확정·회차 실행")
public class PlanController {

    private final PlanAccessService planAccessService;
    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanConfirmService planConfirmService;
    private final PlanStepExecutionService planStepExecutionService;
    private final PlanPreviewService planPreviewService;
    private final PlanPreviewResponseMapper planPreviewResponseMapper;

    public PlanController(
            PlanAccessService planAccessService,
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            PlanConfirmService planConfirmService,
            PlanStepExecutionService planStepExecutionService,
            PlanPreviewService planPreviewService,
            PlanPreviewResponseMapper planPreviewResponseMapper) {
        this.planAccessService = requireNonNull(planAccessService, "planAccessService");
        this.planRepository = requireNonNull(planRepository, "planRepository");
        this.planStepRepository = requireNonNull(planStepRepository, "planStepRepository");
        this.planConfirmService = requireNonNull(planConfirmService, "planConfirmService");
        this.planStepExecutionService = requireNonNull(planStepExecutionService, "planStepExecutionService");
        this.planPreviewService = requireNonNull(planPreviewService, "planPreviewService");
        this.planPreviewResponseMapper = requireNonNull(planPreviewResponseMapper, "planPreviewResponseMapper");
    }

    @Operation(summary = "계획 미리보기")
    @PostMapping("/plans/preview")
    public ApiResponse<PlanPreviewResponse> preview(
            @CurrentUser UUID userId,
            @Valid @RequestBody PlanPreviewRequest request) {
        planAccessService.requireGoalOwner(userId, UUID.fromString(request.goalId()));

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
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody PlanCreateRequest request) {
        UUID goalId = UUID.fromString(id);
        planAccessService.requireGoalOwner(userId, goalId);

        Plan savedPlan = planConfirmService.confirmAndSaveWithSteps(
                goalId,
                request.weeklyBudgetKrw(),
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
    public ApiResponse<PlanVersionListResponse> listPlanVersions(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        UUID goalId = UUID.fromString(id);
        planAccessService.requireGoalOwner(userId, goalId);

        List<Plan> plans = planRepository.findByGoal_Id(goalId);
        List<PlanVersionListResponse.Version> versions = plans.stream()
                .map(PlanResponseMapper::toVersionDto)
                .collect(Collectors.toList());

        return ApiResponse.of(new PlanVersionListResponse(versions));
    }

    @Operation(summary = "활성 계획과 회차")
    @GetMapping("/goals/{id}/plans/active")
    public ApiResponse<ActivePlanResponse> getActivePlan(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        UUID goalId = UUID.fromString(id);
        planAccessService.requireGoalOwner(userId, goalId);

        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId)
                .orElseThrow(() -> new NotFoundException("활성 계획을 찾을 수 없습니다."));

        List<PlanStep> steps = planStepRepository.findByPlan_IdOrderBySeqAsc(activePlan.getId());
        ActivePlanResponse response = PlanResponseMapper.toActivePlanResponse(activePlan, steps);

        return ApiResponse.of(response);
    }

    @Operation(summary = "회차 완료 기록")
    @PostMapping("/plans/{id}/steps/{seq}/complete")
    public ApiResponse<StepCompleteResponse> completeStep(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @PathVariable int seq,
            @RequestBody StepCompleteRequest request) {
        UUID planId = UUID.fromString(id);
        planAccessService.requirePlanOwner(userId, planId);

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
            @CurrentUser UUID userId,
            @PathVariable String id,
            @PathVariable int seq) {
        UUID planId = UUID.fromString(id);

        // 소유자 검증이 계획 조회를 겸한다 — 같은 계획을 두 번 읽지 않는다.
        Plan plan = planAccessService.requirePlanOwner(userId, planId);
        double targetAmount = plan.getGoal().getTargetAmount();

        PlanStepExecutionService.SkipResult skipResult =
                planStepExecutionService.skipStep(planId, seq, targetAmount);

        // achieveProb 는 0 으로 남긴다 — 달성 확률의 정의 자체가 요구사항 v2 §4.12 미확정이다.
        StepSkipResponse response = new StepSkipResponse(
                new StepSkipResponse.Redistributed(
                        skipResult.burdenBefore(),
                        skipResult.burdenAfter(),
                        skipResult.burdenIncreasePct()),
                new StepSkipResponse.AchieveProb(0.0, 0.0),
                skipResult.consecutiveSkips(),
                plan.getVersion()); // 현재 버전

        return ApiResponse.of(response);
    }
}
