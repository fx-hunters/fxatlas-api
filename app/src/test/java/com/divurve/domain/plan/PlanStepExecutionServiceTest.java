package com.divurve.domain.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanStepExecutionService.SkipResult;
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
 * PlanStepExecutionService 테스트.
 * 회차 완료, 회차 건너뛰기, 부담 재분배를 검증한다.
 */
@DisplayName("PlanStepExecutionService")
class PlanStepExecutionServiceTest {

    private PlanRepository planRepository;
    private PlanStepRepository planStepRepository;
    private PlanStepExecutionService service;

    private UUID planId;
    private Plan plan;
    private User owner;
    private Goal goal;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planStepRepository = mock(PlanStepRepository.class);
        service = new PlanStepExecutionService(planRepository, planStepRepository);

        planId = UUID.randomUUID();
        owner = User.create("test@example.com", "Test User", false);
        goal = Goal.builder(owner, "USD Goal", "savings", "travel", "USD")
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

    @Nested
    @DisplayName("completeStep")
    class CompleteStepTest {

        @Test
        @DisplayName("회차 완료 기록")
        void completeStepSuccessfully() {
            // Given
            int seq = 1;
            double executedAmount = 2500.0;
            PlanStep step = PlanStep.create(
                    plan, seq, LocalDate.of(2024, 1, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);

            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.of(step));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // When
            PlanStep result = service.completeStep(planId, seq, executedAmount);

            // Then
            assertEquals(seq, result.getSeq());
            assertEquals(executedAmount, result.getExecutedAmount());
            assertTrue(result.isCompleted());
            verify(planStepRepository).save(result);
        }

        @Test
        @DisplayName("회차를 찾을 수 없으면 예외 발생")
        void stepNotFound() {
            // Given
            int seq = 1;
            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.empty());

            // When, Then
            assertThrows(IllegalArgumentException.class, () ->
                service.completeStep(planId, seq, 2500.0));
        }
    }

    @Nested
    @DisplayName("skipStep")
    class SkipStepTest {

        @Test
        @DisplayName("회차 건너뛰기 처리")
        void skipStepSuccessfully() {
            // Given
            int seq = 1;
            PlanStep step1 = PlanStep.create(
                    plan, 1, LocalDate.of(2024, 1, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step2 = PlanStep.create(
                    plan, 2, LocalDate.of(2024, 2, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step3 = PlanStep.create(
                    plan, 3, LocalDate.of(2024, 3, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step4 = PlanStep.create(
                    plan, 4, LocalDate.of(2024, 4, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);

            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.of(step1));
            when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId))
                    .thenReturn(List.of(step1, step2, step3, step4));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // When
            SkipResult result = service.skipStep(planId, seq, 10000.0);

            // Then
            assertEquals(1, result.consecutiveSkips());
            assertEquals(2500.0, result.burdenBefore());
            assertTrue(result.burdenAfter() > result.burdenBefore());
            verify(planStepRepository).save(step1);
        }

        @Test
        @DisplayName("연속 건너뛰기 판정")
        void consecutiveSkips() {
            // Given
            int seq = 3;
            PlanStep step1 = PlanStep.create(
                    plan, 1, LocalDate.of(2024, 1, 1), 2500.0, 0.0,
                    PlanStepStatus.SKIPPED);
            PlanStep step2 = PlanStep.create(
                    plan, 2, LocalDate.of(2024, 2, 1), 2500.0, 0.0,
                    PlanStepStatus.SKIPPED);
            PlanStep step3 = PlanStep.create(
                    plan, 3, LocalDate.of(2024, 3, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step4 = PlanStep.create(
                    plan, 4, LocalDate.of(2024, 4, 1), 2500.0, 0.0,
                    PlanStepStatus.PENDING);

            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.of(step3));
            when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId))
                    .thenReturn(List.of(step1, step2, step3, step4));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // When
            SkipResult result = service.skipStep(planId, seq, 10000.0);

            // Then
            assertEquals(3, result.consecutiveSkips());
            assertTrue(result.safeModeTriggered());
        }

        @Test
        @DisplayName("회차를 찾을 수 없으면 예외 발생")
        void stepNotFound() {
            // Given
            int seq = 1;
            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.empty());

            // When, Then
            assertThrows(IllegalArgumentException.class, () ->
                service.skipStep(planId, seq, 10000.0));
        }

        @Test
        @DisplayName("건너뛰기 후 부담이 증가한다")
        void burdenIncreases() {
            // Given
            int seq = 1;
            PlanStep step1 = PlanStep.create(
                    plan, 1, LocalDate.of(2024, 1, 1), 1000.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step2 = PlanStep.create(
                    plan, 2, LocalDate.of(2024, 2, 1), 1000.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step3 = PlanStep.create(
                    plan, 3, LocalDate.of(2024, 3, 1), 1000.0, 0.0,
                    PlanStepStatus.PENDING);
            PlanStep step4 = PlanStep.create(
                    plan, 4, LocalDate.of(2024, 4, 1), 1000.0, 0.0,
                    PlanStepStatus.PENDING);

            when(planStepRepository.findByPlan_IdAndSeq(planId, seq))
                    .thenReturn(Optional.of(step1));
            when(planStepRepository.findByPlan_IdOrderBySeqAsc(planId))
                    .thenReturn(List.of(step1, step2, step3, step4));
            when(planStepRepository.save(any())).thenAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // When
            SkipResult result = service.skipStep(planId, seq, 4000.0);

            // Then
            double expectedBurdenAfter = 4000.0 / 3; // 4000을 3회차로 나눔
            assertEquals(1000.0, result.burdenBefore());
            assertEquals(expectedBurdenAfter, result.burdenAfter(), 1e-6);
            assertTrue(result.burdenIncreasePct() > 0);
        }
    }
}
