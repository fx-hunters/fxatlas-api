package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.api.dto.plan.ActivePlanResponse;
import com.divurve.api.dto.plan.PlanCreateRequest;
import com.divurve.api.dto.plan.PlanResponse;
import com.divurve.api.dto.plan.StepCompleteRequest;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanAccessService;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanPreviewService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.plan.PlanStepStatus;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.route.RouteFeatureFlag;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 이슈 #68 — 계획 확정(POST /goals/{id}/plans) 이 회차를 실제로 저장하는지, 그리고
 * 활성 계획 조회·회차 완료/건너뛰기가 그 저장된 회차를 대상으로 동작하는지 검증하는
 * 컨트롤러 → 서비스 → 저장소(실제 Postgres) 왕복 통합 테스트.
 *
 * <p>이 결함은 단위 테스트만으로는 드러나지 않았다 — {@code PlanConfirmService#savePlanSteps}
 * 의 프로덕션 호출처가 0개였는데도 단위 테스트가 그 메서드를 직접 호출해 커버리지를 채웠기
 * 때문이다. 그래서 여기서는 {@code PlanController} 를 실제 리포지토리 위에 수동으로 구성해
 * (컴포넌트 스캔 없이) HTTP 컨트롤러 진입점부터 DB 저장까지 실제 왕복을 확인한다.
 */
@DisplayName("PlanController — 계획 확정 시 회차 저장 (이슈 #68)")
class PlanControllerStepPersistenceIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanStepRepository planStepRepository;

    @Autowired
    private UserRepository userRepository;

    private PlanController controller;

    private UUID ownerId;
    private UUID goalId;

    @BeforeEach
    void setUp() {
        PlanAccessService planAccessService = new PlanAccessService(goalRepository, planRepository);
        PlanConfirmService planConfirmService =
                new PlanConfirmService(goalRepository, planRepository, planStepRepository);
        PlanStepExecutionService planStepExecutionService =
                new PlanStepExecutionService(planRepository, planStepRepository);
        // preview 는 이 테스트 범위 밖이라 실제 계산기 없이 mock 으로 대체한다.
        PlanPreviewService planPreviewService = org.mockito.Mockito.mock(PlanPreviewService.class);
        com.divurve.api.dto.plan.PlanPreviewResponseMapper planPreviewResponseMapper =
                new com.divurve.api.dto.plan.PlanPreviewResponseMapper();

        controller = new PlanController(
                planAccessService,
                planRepository,
                planStepRepository,
                planConfirmService,
                planStepExecutionService,
                planPreviewService,
                planPreviewResponseMapper,
                new RouteFeatureFlag(true));

        User owner = userRepository.save(User.createDemo("plan-steps-" + UUID.randomUUID() + "@divurve.com", "사용자"));
        ownerId = owner.getId();
        Goal goal = goalRepository.save(Goal.builder(owner, "미국 여행 자금", "onetime", "spend", "USD")
                .targetAmount(4000.0)
                .budgetAmount(2_000_000)
                .status("active")
                .recurInterval("MONTHLY")
                .build());
        goalId = goal.getId();
    }

    @Test
    @DisplayName("createPlan 은 회차를 저장하고, 확정 응답에 채워진 steps 를 반환한다")
    void createPlanPersistsSteps() {
        PlanCreateRequest request = new PlanCreateRequest(100_000L, 0.8, 4);

        ApiResponse<PlanResponse> response = controller.createPlan(ownerId, goalId.toString(), request);

        assertThat(response.data().steps()).hasSize(4);
        assertThat(response.data().steps())
                .extracting(PlanResponse.Step::seq)
                .containsExactly(1, 2, 3, 4);
        assertThat(response.data().steps())
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(PlanStepStatus.PENDING));

        // 확정 시 반환한 seq 순서가 DB 에 실제로 저장됐는지 리포지토리로 다시 확인한다.
        UUID planId = UUID.fromString(response.data().id());
        assertThat(planStepRepository.findByPlan_IdOrderBySeqAsc(planId)).hasSize(4);
    }

    @Test
    @DisplayName("createPlan 으로 저장된 회차가 GET /goals/{id}/plans/active 에도 그대로 나타난다")
    void activePlanReflectsPersistedSteps() {
        controller.createPlan(ownerId, goalId.toString(), new PlanCreateRequest(100_000L, 0.8, 4));

        ApiResponse<ActivePlanResponse> activeResponse = controller.getActivePlan(ownerId, goalId.toString());

        assertThat(activeResponse.data().steps()).hasSize(4);
    }

    @Test
    @DisplayName("createPlan 으로 저장된 1회차를 완료 처리할 수 있다 (완결 왕복)")
    void completeStepWorksOnPersistedStep() {
        controller.createPlan(ownerId, goalId.toString(), new PlanCreateRequest(100_000L, 0.8, 4));
        // completeStep 은 planId 를 경로변수로 받으므로 확정 후 활성 계획을 다시 조회해 실제 planId 를 얻는다.
        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId).orElseThrow();

        var response = controller.completeStep(
                ownerId, activePlan.getId().toString(), 1, new StepCompleteRequest(800.0, 1350.0));

        assertThat(response.data().status()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(response.data().executedAmount()).isEqualTo(800.0);
    }

    @Test
    @DisplayName("이미 완료된 회차를 다시 완료하면 400(VALIDATION_FAILED) 이다")
    void completingAlreadyCompletedStepFails() {
        controller.createPlan(ownerId, goalId.toString(), new PlanCreateRequest(100_000L, 0.8, 4));
        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId).orElseThrow();
        String planId = activePlan.getId().toString();

        controller.completeStep(ownerId, planId, 1, new StepCompleteRequest(800.0, 1350.0));

        assertThatThrownBy(() ->
                controller.completeStep(ownerId, planId, 1, new StepCompleteRequest(800.0, 1350.0)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("건너뛴 회차를 완료 처리하면 400(VALIDATION_FAILED) 이다")
    void completingSkippedStepFails() {
        controller.createPlan(ownerId, goalId.toString(), new PlanCreateRequest(100_000L, 0.8, 4));
        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId).orElseThrow();
        String planId = activePlan.getId().toString();

        controller.skipStep(ownerId, planId, 1);

        assertThatThrownBy(() ->
                controller.completeStep(ownerId, planId, 1, new StepCompleteRequest(800.0, 1350.0)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("이미 완료된 회차를 건너뛰면 400(VALIDATION_FAILED) 이다")
    void skippingCompletedStepFails() {
        controller.createPlan(ownerId, goalId.toString(), new PlanCreateRequest(100_000L, 0.8, 4));
        Plan activePlan = planRepository.findByGoal_IdAndIsActiveTrue(goalId).orElseThrow();
        String planId = activePlan.getId().toString();

        controller.completeStep(ownerId, planId, 1, new StepCompleteRequest(800.0, 1350.0));

        assertThatThrownBy(() -> controller.skipStep(ownerId, planId, 1))
                .isInstanceOf(InvalidRequestException.class);
    }
}
