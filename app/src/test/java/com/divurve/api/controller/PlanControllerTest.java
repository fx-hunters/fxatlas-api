package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.plan.ActivePlanResponse;
import com.divurve.api.dto.plan.PlanCreateRequest;
import com.divurve.api.dto.plan.PlanPreviewRequest;
import com.divurve.api.dto.plan.PlanResponse;
import com.divurve.api.dto.plan.PlanVersionListResponse;
import com.divurve.api.dto.plan.StepCompleteRequest;
import com.divurve.api.dto.plan.StepCompleteResponse;
import com.divurve.api.dto.plan.StepSkipResponse;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepExecutionService.SkipResult;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.plan.PlanStepStatus;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanController} — 도메인 결과 → DTO 변환, data/meta 래핑, 미구현·미존재 예외 검증.
 */
@DisplayName("PlanController")
class PlanControllerTest {

    private GoalRepository goalRepository;
    private PlanRepository planRepository;
    private PlanStepRepository planStepRepository;
    private PlanConfirmService planConfirmService;
    private PlanStepExecutionService planStepExecutionService;
    private PlanController controller;

    private UUID goalId;
    private UUID planId;
    private Goal goal;
    private Plan plan;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        planRepository = mock(PlanRepository.class);
        planStepRepository = mock(PlanStepRepository.class);
        planConfirmService = mock(PlanConfirmService.class);
        planStepExecutionService = mock(PlanStepExecutionService.class);
        controller = new PlanController(
                goalRepository, planRepository, planStepRepository,
                planConfirmService, planStepExecutionService);

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
    @DisplayName("preview 는 아직 구현되지 않았다")
    void previewIsNotImplemented() {
        PlanPreviewRequest request = new PlanPreviewRequest(goalId.toString(), 100000L, 0.8, 4);

        assertThatThrownBy(() -> controller.preview(request))
                .isInstanceOf(NotImplementedException.class);
    }

    @Test
    @DisplayName("createPlan 은 확정된 계획과 회차를 data/meta 로 감싼다")
    void createPlanWrapsSavedPlan() {
        PlanCreateRequest request = new PlanCreateRequest(100000L, 0.8, 4);
        when(planConfirmService.confirmAndSavePlan(goalId, 0.8, 4, 0.0, 0.0, null))
                .thenReturn(plan);
        when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId)).thenReturn(steps());

        ApiResponse<PlanResponse> response = controller.createPlan(goalId.toString(), request);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().id()).isEqualTo(planId.toString());
        assertThat(response.data().goalId()).isEqualTo(goalId.toString());
        assertThat(response.data().version()).isEqualTo(2);
        assertThat(response.data().steps()).hasSize(2);
        assertThat(response.data().steps().get(0).scheduledDate()).isEqualTo("2024-01-01");
        assertThat(response.data().steps().get(1).scheduledDate()).isNull();
        verify(planConfirmService).confirmAndSavePlan(goalId, 0.8, 4, 0.0, 0.0, null);
    }

    @Test
    @DisplayName("listPlanVersions 는 버전 이력을 반환한다")
    void listPlanVersionsReturnsHistory() {
        when(planRepository.findByGoal_Id(goalId)).thenReturn(List.of(plan));

        ApiResponse<PlanVersionListResponse> response =
                controller.listPlanVersions(goalId.toString());

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

        ApiResponse<ActivePlanResponse> response = controller.getActivePlan(goalId.toString());

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().id()).isEqualTo(planId.toString());
        assertThat(response.data().steps()).hasSize(2);
    }

    @Test
    @DisplayName("getActivePlan 은 활성 계획이 없으면 예외를 던진다")
    void getActivePlanThrowsWhenMissing() {
        when(planRepository.findByGoal_IdAndIsActiveTrue(goalId)).thenReturn(Optional.empty());

        String id = goalId.toString();
        assertThatThrownBy(() -> controller.getActivePlan(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Active plan not found");
    }

    @Test
    @DisplayName("completeStep 은 완료된 회차를 반환한다")
    void completeStepReturnsCompletedStep() {
        StepCompleteRequest request = new StepCompleteRequest(2500.0, 1350.0);
        PlanStep completed = PlanStep.create(
                plan, 1, LocalDate.of(2024, 1, 1), 2500.0, 2500.0, PlanStepStatus.COMPLETED);
        when(planStepExecutionService.completeStep(planId, 1, 2500.0)).thenReturn(completed);

        ApiResponse<StepCompleteResponse> response =
                controller.completeStep(planId.toString(), 1, request);

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
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(planStepExecutionService.skipStep(planId, 2, 10000.0))
                .thenReturn(new SkipResult(2, 2500.0, 3750.0, 50.0, false, 7500.0, 2));

        ApiResponse<StepSkipResponse> response = controller.skipStep(planId.toString(), 2);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().redistributed().perStepBefore()).isEqualTo(2500.0);
        assertThat(response.data().redistributed().perStepAfter()).isEqualTo(3750.0);
        assertThat(response.data().redistributed().increasePct()).isEqualTo(50.0);
        assertThat(response.data().achieveProb().before()).isZero();
        assertThat(response.data().achieveProb().after()).isZero();
        assertThat(response.data().consecutiveSkips()).isEqualTo(2);
        assertThat(response.data().safeModeTriggered()).isFalse();
        assertThat(response.data().newPlanVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("skipStep 은 계획이 없으면 예외를 던진다")
    void skipStepThrowsWhenPlanMissing() {
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        String id = planId.toString();
        assertThatThrownBy(() -> controller.skipStep(id, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan not found");
    }
}
