package com.divurve.domain.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import com.divurve.domain.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

/**
 * PlanConfirmService 테스트.
 * 계획 확정, 버전 관리, 회차 생성을 검증한다.
 */
@DisplayName("PlanConfirmService")
class PlanConfirmServiceTest {

    private GoalRepository goalRepository;
    private PlanRepository planRepository;
    private PlanStepRepository planStepRepository;
    private PlanConfirmService service;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        planRepository = mock(PlanRepository.class);
        planStepRepository = mock(PlanStepRepository.class);
        service = new PlanConfirmService(goalRepository, planRepository, planStepRepository);
    }

    @Nested
    @DisplayName("confirmAndSavePlan")
    class ConfirmAndSavePlanTest {

        private UUID goalId;
        private Goal goal;
        private User owner;

        @BeforeEach
        void setUp() {
            goalId = UUID.randomUUID();
            owner = User.create("test@example.com", "Test User", null);
            goal = Goal.builder(owner, "USD Goal", "savings", "travel", "USD")
                    .targetAmount(10000.0)
                    .build();
        }

        @Test
        @DisplayName("새로운 계획 생성 (첫 버전)")
        void createFirstVersion() {
            // Given
            when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
            when(planRepository.findTopByGoal_IdOrderByVersionDesc(goalId))
                    .thenReturn(Optional.empty());
            when(planRepository.findByGoal_IdAndIsActiveTrue(goalId))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenAnswer(invocation -> {
                Plan plan = invocation.getArgument(0);
                // ID와 created_at 할당 (실제 DB 처럼)
                return plan;
            });

            // When
            Plan result = service.confirmAndSavePlan(
                    goalId,
                    0.8,  // safeRatio
                    4,    // splitCount
                    1000.0, // opportunityAmount
                    110.0,  // triggerRate
                    null);  // changeReason

            // Then
            assertNotNull(result);
            assertEquals(1, result.getVersion());
            assertTrue(result.isActive());
            assertEquals(0.8, result.getSafeRatio());
            assertEquals(4, result.getSplitCount());
            assertEquals(1000.0, result.getOpportunityAmount());
            assertEquals(110.0, result.getOpportunityTriggerRate());
            verify(planRepository).save(result);
        }

        @Test
        @DisplayName("새로운 계획 생성 (버전 증가)")
        void createNextVersion() {
            // Given
            UUID previousPlanId = UUID.randomUUID();
            Plan previousPlan = Plan.builder(goal, 1)
                    .isActive(true)
                    .safeRatio(0.7)
                    .splitCount(3)
                    .opportunityAmount(500.0)
                    .opportunityTriggerRate(105.0)
                    .build();

            when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
            when(planRepository.findTopByGoal_IdOrderByVersionDesc(goalId))
                    .thenReturn(Optional.of(previousPlan));
            when(planRepository.findByGoal_IdAndIsActiveTrue(goalId))
                    .thenReturn(Optional.of(previousPlan));
            when(planRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // When
            Plan result = service.confirmAndSavePlan(
                    goalId,
                    0.8,
                    4,
                    1000.0,
                    110.0,
                    "Updated volatility");

            // Then
            assertEquals(2, result.getVersion());
            assertTrue(result.isActive());
            assertEquals("Updated volatility", result.getReason());
            verify(planRepository).save(result);
        }

        @Test
        @DisplayName("목표를 찾을 수 없으면 예외 발생")
        void goalNotFound() {
            // Given
            when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

            // When, Then
            assertThrows(IllegalArgumentException.class, () ->
                service.confirmAndSavePlan(goalId, 0.8, 4, 1000.0, 110.0, null));
        }
    }

    @Nested
    @DisplayName("savePlanSteps")
    class SavePlanStepsTest {

        private UUID planId;
        private Plan plan;

        @BeforeEach
        void setUp() {
            planId = UUID.randomUUID();
            User owner = User.create("test@example.com", "Test User", null);
            Goal goal = Goal.builder(owner, "USD Goal", "savings", "travel", "USD")
                    .targetAmount(10000.0)
                    .build();
            plan = Plan.builder(goal, 1)
                    .isActive(true)
                    .safeRatio(0.8)
                    .splitCount(4)
                    .opportunityAmount(1000.0)
                    .opportunityTriggerRate(110.0)
                    .build();
        }

        @Test
        @DisplayName("회차 목록을 저장한다")
        void saveSingleStep() {
            // Given
            when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            List<PlanConfirmService.StepInput> steps = List.of(
                new PlanConfirmService.StepInput(1, LocalDate.of(2024, 1, 1), 2500.0)
            );

            // When
            service.savePlanSteps(planId, steps);

            // Then
            verify(planStepRepository).save(any(PlanStep.class));
        }

        @Test
        @DisplayName("여러 회차를 저장한다")
        void saveMultipleSteps() {
            // Given
            when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            List<PlanConfirmService.StepInput> steps = List.of(
                new PlanConfirmService.StepInput(1, LocalDate.of(2024, 1, 1), 2500.0),
                new PlanConfirmService.StepInput(2, LocalDate.of(2024, 2, 1), 2500.0),
                new PlanConfirmService.StepInput(3, LocalDate.of(2024, 3, 1), 2500.0),
                new PlanConfirmService.StepInput(4, LocalDate.of(2024, 4, 1), 2500.0)
            );

            // When
            service.savePlanSteps(planId, steps);

            // Then
            verify(planStepRepository, times(steps.size())).save(any(PlanStep.class));
        }

        @Test
        @DisplayName("계획을 찾을 수 없으면 예외 발생")
        void planNotFound() {
            // Given
            when(planRepository.findById(planId)).thenReturn(Optional.empty());

            List<PlanConfirmService.StepInput> steps = List.of(
                new PlanConfirmService.StepInput(1, LocalDate.of(2024, 1, 1), 2500.0)
            );

            // When, Then
            assertThrows(IllegalArgumentException.class, () ->
                service.savePlanSteps(planId, steps));
        }
    }
}
