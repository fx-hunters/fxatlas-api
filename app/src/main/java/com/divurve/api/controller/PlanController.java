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
import com.divurve.domain.route.RouteFeatureFlag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * <p><b>6개 엔드포인트 전부 {@link RouteFeatureFlag} 뒤에 있다.</b> 요구사항 v2 §4.12 는 Route 의
 * 목적함수 · 안전/기회 버킷 존재와 비율 · 목적별 하한선 · 권장 분할 회차 · 몬테카를로 적용 여부 ·
 * 달성 확률 정의를 <b>전부 미확정</b>으로 선언했고, 명세 v2 §6 은 계산 엔드포인트를 아예 명세하지
 * 않는다. 그래서 플래그가 꺼진 기본 상태에서는 소유자 검증·계산에 진입하지 않고 곧바로
 * {@code 501 NOT_IMPLEMENTED} 로 끝낸다 — 확정되지 않은 수치가 API 로 새어 나가지 않게 하는 것이
 * 이 게이트의 목적이다.
 *
 * <p>모든 엔드포인트가 {@link PlanAccessService} 로 소유자를 먼저 검증한다 (이슈 #50).
 * 그 이전에는 <b>소유자 검증이 전혀 없어</b> {@code goalId}/{@code planId} 만 알면
 * 남의 계획을 읽고 회차를 완료·건너뛸 수 있었다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Plan", description = "계획 미리보기·확정·회차 실행 (P — Route 확정 전까지 전부 501)")
public class PlanController {

    private final PlanAccessService planAccessService;
    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanConfirmService planConfirmService;
    private final PlanStepExecutionService planStepExecutionService;
    private final PlanPreviewService planPreviewService;
    private final PlanPreviewResponseMapper planPreviewResponseMapper;
    private final RouteFeatureFlag routeFeatureFlag;

    public PlanController(
            PlanAccessService planAccessService,
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            PlanConfirmService planConfirmService,
            PlanStepExecutionService planStepExecutionService,
            PlanPreviewService planPreviewService,
            PlanPreviewResponseMapper planPreviewResponseMapper,
            RouteFeatureFlag routeFeatureFlag) {
        this.planAccessService = requireNonNull(planAccessService, "planAccessService");
        this.planRepository = requireNonNull(planRepository, "planRepository");
        this.planStepRepository = requireNonNull(planStepRepository, "planStepRepository");
        this.planConfirmService = requireNonNull(planConfirmService, "planConfirmService");
        this.planStepExecutionService = requireNonNull(planStepExecutionService, "planStepExecutionService");
        this.planPreviewService = requireNonNull(planPreviewService, "planPreviewService");
        this.planPreviewResponseMapper = requireNonNull(planPreviewResponseMapper, "planPreviewResponseMapper");
        this.routeFeatureFlag = requireNonNull(routeFeatureFlag, "routeFeatureFlag");
    }

    @Operation(
            summary = "계획 미리보기 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PostMapping("/plans/preview")
    public ApiResponse<PlanPreviewResponse> preview(
            @CurrentUser UUID userId,
            @RequestBody PlanPreviewRequest request) {
        routeFeatureFlag.requireEnabled();
        planAccessService.requireGoalOwner(userId, UUID.fromString(request.goalId()));

        var previewInfo = planPreviewService.generatePreview(
                request.goalId(),
                request.weeklyBudgetKrw(),
                request.safeRatio(),
                request.splitCount()
        );

        return ApiResponse.of(planPreviewResponseMapper.toResponse(previewInfo));
    }

    @Operation(
            summary = "계획 확정·저장 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PostMapping("/goals/{id}/plans")
    public ApiResponse<PlanResponse> createPlan(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody PlanCreateRequest request) {
        routeFeatureFlag.requireEnabled();
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

    @Operation(
            summary = "계획 버전 이력 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @GetMapping("/goals/{id}/plans")
    public ApiResponse<PlanVersionListResponse> listPlanVersions(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        routeFeatureFlag.requireEnabled();
        UUID goalId = UUID.fromString(id);
        planAccessService.requireGoalOwner(userId, goalId);

        List<Plan> plans = planRepository.findByGoal_Id(goalId);
        List<PlanVersionListResponse.Version> versions = plans.stream()
                .map(PlanResponseMapper::toVersionDto)
                .collect(Collectors.toList());

        return ApiResponse.of(new PlanVersionListResponse(versions));
    }

    @Operation(
            summary = "활성 계획과 회차 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @GetMapping("/goals/{id}/plans/active")
    public ApiResponse<ActivePlanResponse> getActivePlan(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        routeFeatureFlag.requireEnabled();
        UUID goalId = UUID.fromString(id);
        planAccessService.requireGoalOwner(userId, goalId);

        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId)
                .orElseThrow(() -> new NotFoundException("활성 계획을 찾을 수 없습니다."));

        List<PlanStep> steps = planStepRepository.findByPlan_IdOrderBySeqAsc(activePlan.getId());
        ActivePlanResponse response = PlanResponseMapper.toActivePlanResponse(activePlan, steps);

        return ApiResponse.of(response);
    }

    @Operation(
            summary = "회차 완료 기록 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PostMapping("/plans/{id}/steps/{seq}/complete")
    public ApiResponse<StepCompleteResponse> completeStep(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @PathVariable int seq,
            @RequestBody StepCompleteRequest request) {
        routeFeatureFlag.requireEnabled();
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

    @Operation(
            summary = "회차 건너뛰기 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다(명세 v2 §6.2).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PostMapping("/plans/{id}/steps/{seq}/skip")
    public ApiResponse<StepSkipResponse> skipStep(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @PathVariable int seq) {
        routeFeatureFlag.requireEnabled();
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
