package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.plan.ActivePlanResponse;
import com.divurve.api.dto.plan.PlanCreateRequest;
import com.divurve.api.dto.plan.PlanPreviewRequest;
import com.divurve.api.dto.plan.PlanPreviewResponse;
import com.divurve.api.dto.plan.PlanPreviewResponseMapper;
import com.divurve.api.dto.plan.PlanResponse;
import com.divurve.api.dto.plan.PlanVersionListResponse;
import com.divurve.api.dto.plan.StepCompleteRequest;
import com.divurve.api.dto.plan.StepCompleteResponse;
import com.divurve.api.dto.plan.StepSkipResponse;
import com.divurve.common.exception.NotFoundException;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanAccessService;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanPreviewService.PlanPreviewInfo;
import com.divurve.domain.plan.PlanPreviewService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepExecutionService.SkipResult;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.plan.PlanStepStatus;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import com.divurve.domain.route.RouteFeatureFlag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanController} — 도메인 결과 → DTO 변환, data/meta 래핑, 미존재 예외,
 * 그리고 소유자 격리 (이슈 #50) 검증.
 */
@DisplayName("PlanController")
class PlanControllerTest {

    private PlanAccessService planAccessService;
    private PlanRepository planRepository;
    private PlanStepRepository planStepRepository;
    private PlanConfirmService planConfirmService;
    private PlanStepExecutionService planStepExecutionService;
    private PlanPreviewService planPreviewService;
    private PlanPreviewResponseMapper planPreviewResponseMapper;
    private PlanController controller;

    /** route.enabled=false — 기본값. 6개 엔드포인트 전부 501 이어야 한다. */
    private PlanController disabledController;

    private UUID userId;
    private UUID goalId;
    private UUID planId;
    private Goal goal;
    private Plan plan;

    @BeforeEach
    void setUp() {
        planAccessService = mock(PlanAccessService.class);
        planRepository = mock(PlanRepository.class);
        planStepRepository = mock(PlanStepRepository.class);
        planConfirmService = mock(PlanConfirmService.class);
        planStepExecutionService = mock(PlanStepExecutionService.class);
        planPreviewService = mock(PlanPreviewService.class);
        planPreviewResponseMapper = mock(PlanPreviewResponseMapper.class);
        controller = new PlanController(
                planAccessService, planRepository, planStepRepository,
                planConfirmService, planStepExecutionService,
                planPreviewService, planPreviewResponseMapper,
                new RouteFeatureFlag(true));
        disabledController = new PlanController(
                planAccessService, planRepository, planStepRepository,
                planConfirmService, planStepExecutionService,
                planPreviewService, planPreviewResponseMapper,
                new RouteFeatureFlag(false));

        userId = UUID.randomUUID();
        goalId = UUID.randomUUID();
        planId = UUID.randomUUID();

        goal = mock(Goal.class);
        when(goal.getId()).thenReturn(goalId);
        when(goal.getTargetAmount()).thenReturn(10000.0);

        plan = mock(Plan.class);
        when(plan.getId()).thenReturn(planId);
        when(plan.getGoal()).thenReturn(goal);
        when(plan.getVersion()).thenReturn(2);
        when(plan.isActive()).thenReturn(true);
        when(plan.getReason()).thenReturn("변동성 확대");
        when(plan.getSafeRatio()).thenReturn(0.8);
        when(plan.getSplitCount()).thenReturn(4);
        when(plan.getOpportunityAmount()).thenReturn(1000.0);
        when(plan.getOpportunityTriggerRate()).thenReturn(110.0);
        when(plan.getCreatedAt()).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
    }

    /** 일정일이 있는 회차와 없는 회차를 함께 돌려준다 (매퍼의 null 분기까지 태운다). */
    private List<PlanStep> steps() {
        return List.of(
                PlanStep.create(plan, 1, LocalDate.of(2024, 1, 1), 2500.0, 2500.0,
                        PlanStepStatus.COMPLETED),
                PlanStep.create(plan, 2, null, 2500.0, 0.0, PlanStepStatus.PENDING));
    }

    @Test
    @DisplayName("preview 는 요청 네 필드를 그대로 서비스에 넘기고 매퍼 결과를 data/meta 로 감싼다")
    void previewDelegatesToServiceAndMapper() {
        // 이슈 #19 시점에는 preview 가 스텁이라 501 을 단언했으나, 이슈 #18 에서 실구현되었다.
        // 병합 과정에서 옛 단언이 남아 있었다(이슈 #38).
        PlanPreviewRequest request = new PlanPreviewRequest(goalId.toString(), 100000L, 0.8, 4);
        PlanPreviewInfo info = mock(PlanPreviewInfo.class);
        PlanPreviewResponse mapped = mock(PlanPreviewResponse.class);
        when(planPreviewService.generatePreview(goalId.toString(), 100000L, 0.8, 4)).thenReturn(info);
        when(planPreviewResponseMapper.toResponse(info)).thenReturn(mapped);

        ApiResponse<PlanPreviewResponse> response = controller.preview(userId, request);

        verify(planPreviewService).generatePreview(goalId.toString(), 100000L, 0.8, 4);
        verify(planPreviewResponseMapper).toResponse(info);
        assertThat(response.data()).isSameAs(mapped);
        assertThat(response.meta()).isNotNull();
    }

    @Test
    @DisplayName("createPlan 은 확정된 계획과 회차를 data/meta 로 감싼다")
    void createPlanWrapsSavedPlan() {
        PlanCreateRequest request = new PlanCreateRequest(100000L, 0.8, 4);
        when(planConfirmService.confirmAndSaveWithSteps(goalId, 100000L, 0.8, 4, 0.0, 0.0, null))
                .thenReturn(plan);
        when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId)).thenReturn(steps());

        ApiResponse<PlanResponse> response = controller.createPlan(userId, goalId.toString(), request);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().id()).isEqualTo(planId.toString());
        assertThat(response.data().goalId()).isEqualTo(goalId.toString());
        assertThat(response.data().version()).isEqualTo(2);
        assertThat(response.data().steps()).hasSize(2);
        assertThat(response.data().steps().get(0).scheduledDate()).isEqualTo("2024-01-01");
        assertThat(response.data().steps().get(1).scheduledDate()).isNull();
        verify(planConfirmService).confirmAndSaveWithSteps(goalId, 100000L, 0.8, 4, 0.0, 0.0, null);
    }

    @Test
    @DisplayName("listPlanVersions 는 버전 이력을 반환한다")
    void listPlanVersionsReturnsHistory() {
        when(planRepository.findByGoal_Id(goalId)).thenReturn(List.of(plan));

        ApiResponse<PlanVersionListResponse> response =
                controller.listPlanVersions(userId, goalId.toString());

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().versions()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(planId.toString());
            assertThat(v.version()).isEqualTo(2);
            assertThat(v.isActive()).isTrue();
            assertThat(v.reason()).isEqualTo("변동성 확대");
            assertThat(v.createdAt()).isEqualTo("2024-01-01T00:00:00Z");
        });
    }

    @Test
    @DisplayName("getActivePlan 은 활성 계획과 회차를 반환한다")
    void getActivePlanReturnsActivePlan() {
        when(planRepository.findByGoal_IdAndIsActiveTrue(goalId)).thenReturn(Optional.of(plan));
        when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId)).thenReturn(steps());

        ApiResponse<ActivePlanResponse> response = controller.getActivePlan(userId, goalId.toString());

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().id()).isEqualTo(planId.toString());
        assertThat(response.data().steps()).hasSize(2);
    }

    /** 이슈 #50 이전에는 IllegalArgumentException 이라 500 이 나갔다 — 자원 부재는 404 다. */
    @Test
    @DisplayName("getActivePlan 은 활성 계획이 없으면 404 를 던진다")
    void getActivePlanThrowsWhenMissing() {
        when(planRepository.findByGoal_IdAndIsActiveTrue(goalId)).thenReturn(Optional.empty());

        String id = goalId.toString();
        assertThatThrownBy(() -> controller.getActivePlan(userId, id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("활성 계획을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("completeStep 은 완료된 회차를 반환한다")
    void completeStepReturnsCompletedStep() {
        StepCompleteRequest request = new StepCompleteRequest(2500.0, 1350.0);
        PlanStep completed = PlanStep.create(
                plan, 1, LocalDate.of(2024, 1, 1), 2500.0, 2500.0, PlanStepStatus.COMPLETED);
        when(planStepExecutionService.completeStep(planId, 1, 2500.0)).thenReturn(completed);

        ApiResponse<StepCompleteResponse> response =
                controller.completeStep(userId, planId.toString(), 1, request);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().seq()).isEqualTo(1);
        assertThat(response.data().status()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(response.data().executedAmount()).isEqualTo(2500.0);
        assertThat(response.data().executedRate()).isEqualTo(1350.0);
        assertThat(response.data().remainingAmount()).isZero();
    }

    @Test
    @DisplayName("skipStep 은 재분배 결과를 반환한다")
    void skipStepReturnsRedistribution() {
        when(planAccessService.requirePlanOwner(userId, planId)).thenReturn(plan);
        when(planStepExecutionService.skipStep(planId, 2, 10000.0))
                .thenReturn(new SkipResult(2, 2500.0, 3750.0, 50.0, 7500.0, 2));

        ApiResponse<StepSkipResponse> response = controller.skipStep(userId, planId.toString(), 2);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().redistributed().perStepBefore()).isEqualTo(2500.0);
        assertThat(response.data().redistributed().perStepAfter()).isEqualTo(3750.0);
        assertThat(response.data().redistributed().increasePct()).isEqualTo(50.0);
        assertThat(response.data().achieveProb().before()).isZero();
        assertThat(response.data().achieveProb().after()).isZero();
        assertThat(response.data().consecutiveSkips()).isEqualTo(2);
        assertThat(response.data().newPlanVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("skipStep 은 계획이 없거나 남의 계획이면 404 를 던지고 실행 서비스를 부르지 않는다")
    void skipStepThrowsWhenPlanMissing() {
        when(planAccessService.requirePlanOwner(userId, planId))
                .thenThrow(new NotFoundException("계획을 찾을 수 없습니다."));

        String id = planId.toString();
        assertThatThrownBy(() -> controller.skipStep(userId, id, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("계획을 찾을 수 없습니다");
        verifyNoInteractions(planStepExecutionService);
    }

    // ── 소유자 격리 (이슈 #50) ──────────────────────────────────────────
    // 이전에는 소유자 검증이 전혀 없어 goalId/planId 만 알면 남의 계획을 읽고 조작할 수 있었다.

    @Test
    @DisplayName("preview 는 남의 목표면 404 를 던지고 미리보기 계산을 하지 않는다")
    void previewRejectsForeignGoal() {
        when(planAccessService.requireGoalOwner(userId, goalId))
                .thenThrow(new NotFoundException("목표를 찾을 수 없습니다."));
        PlanPreviewRequest request = new PlanPreviewRequest(goalId.toString(), 100000L, 0.8, 4);

        assertThatThrownBy(() -> controller.preview(userId, request))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(planPreviewService, planPreviewResponseMapper);
    }

    @Test
    @DisplayName("createPlan 은 남의 목표면 404 를 던지고 계획을 저장하지 않는다")
    void createPlanRejectsForeignGoal() {
        when(planAccessService.requireGoalOwner(userId, goalId))
                .thenThrow(new NotFoundException("목표를 찾을 수 없습니다."));
        String id = goalId.toString();
        PlanCreateRequest request = new PlanCreateRequest(100000L, 0.8, 4);

        assertThatThrownBy(() -> controller.createPlan(userId, id, request))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(planConfirmService);
    }

    @Test
    @DisplayName("listPlanVersions 는 남의 목표면 404 를 던지고 이력을 읽지 않는다")
    void listPlanVersionsRejectsForeignGoal() {
        when(planAccessService.requireGoalOwner(userId, goalId))
                .thenThrow(new NotFoundException("목표를 찾을 수 없습니다."));
        String id = goalId.toString();

        assertThatThrownBy(() -> controller.listPlanVersions(userId, id))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(planRepository);
    }

    @Test
    @DisplayName("getActivePlan 은 남의 목표면 404 를 던지고 활성 계획을 읽지 않는다")
    void getActivePlanRejectsForeignGoal() {
        when(planAccessService.requireGoalOwner(userId, goalId))
                .thenThrow(new NotFoundException("목표를 찾을 수 없습니다."));
        String id = goalId.toString();

        assertThatThrownBy(() -> controller.getActivePlan(userId, id))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(planRepository);
    }

    @Test
    @DisplayName("completeStep 은 남의 계획이면 404 를 던지고 회차를 완료하지 않는다")
    void completeStepRejectsForeignPlan() {
        when(planAccessService.requirePlanOwner(userId, planId))
                .thenThrow(new NotFoundException("계획을 찾을 수 없습니다."));
        String id = planId.toString();
        StepCompleteRequest request = new StepCompleteRequest(2500.0, 1350.0);

        assertThatThrownBy(() -> controller.completeStep(userId, id, 1, request))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(planStepExecutionService);
    }
    // ── Route 강등 (요구사항 v2 §4.12 미확정, 명세 v2 §6) ──────────────────
    // route.enabled 기본값은 false 다. 확정되지 않은 수치(버킷 하한 · 분할 회차 ·
    // 몬테카를로 · 달성 확률)가 API 로 새어 나가지 않도록 6개 전부 501 로 막고,
    // 소유자 검증·계산 서비스에 진입조차 하지 않는다.

    @Test
    @DisplayName("route.enabled=false 면 preview 는 501 이고 계산에 진입하지 않는다")
    void previewReturns501WhenRouteDisabled() {
        PlanPreviewRequest request = new PlanPreviewRequest(goalId.toString(), 100000L, 0.8, 4);

        assertThatThrownBy(() -> disabledController.preview(userId, request))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planPreviewService, planPreviewResponseMapper);
    }

    @Test
    @DisplayName("route.enabled=false 면 createPlan 은 501 이고 계획을 저장하지 않는다")
    void createPlanReturns501WhenRouteDisabled() {
        String id = goalId.toString();
        PlanCreateRequest request = new PlanCreateRequest(100000L, 0.8, 4);

        assertThatThrownBy(() -> disabledController.createPlan(userId, id, request))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planConfirmService, planStepRepository);
    }

    @Test
    @DisplayName("route.enabled=false 면 listPlanVersions 는 501 이다")
    void listPlanVersionsReturns501WhenRouteDisabled() {
        String id = goalId.toString();

        assertThatThrownBy(() -> disabledController.listPlanVersions(userId, id))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planRepository);
    }

    @Test
    @DisplayName("route.enabled=false 면 getActivePlan 은 501 이다")
    void getActivePlanReturns501WhenRouteDisabled() {
        String id = goalId.toString();

        assertThatThrownBy(() -> disabledController.getActivePlan(userId, id))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planRepository);
    }

    @Test
    @DisplayName("route.enabled=false 면 completeStep 은 501 이고 회차를 완료하지 않는다")
    void completeStepReturns501WhenRouteDisabled() {
        String id = planId.toString();
        StepCompleteRequest request = new StepCompleteRequest(2500.0, 1350.0);

        assertThatThrownBy(() -> disabledController.completeStep(userId, id, 1, request))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planStepExecutionService);
    }

    @Test
    @DisplayName("route.enabled=false 면 skipStep 은 501 이고 재분배를 계산하지 않는다")
    void skipStepReturns501WhenRouteDisabled() {
        String id = planId.toString();

        assertThatThrownBy(() -> disabledController.skipStep(userId, id, 2))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(planAccessService, planStepExecutionService);
    }
}
