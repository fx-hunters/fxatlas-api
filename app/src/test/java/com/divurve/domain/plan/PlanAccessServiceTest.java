package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanAccessService} 소유자 격리 검증 (이슈 #50, NFR-SE-03).
 *
 * <p>핵심은 <b>"없음"과 "남의 것"이 구분되지 않는다</b>는 점이다 — 둘 다 같은 404·같은 메시지여야
 * 공격자가 응답 차이로 id 의 존재 여부를 알아낼 수 없다.
 */
@DisplayName("PlanAccessService")
class PlanAccessServiceTest {

    private GoalRepository goalRepository;
    private PlanRepository planRepository;
    private PlanAccessService service;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID goalId;
    private UUID planId;
    private Goal goal;
    private Plan plan;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        planRepository = mock(PlanRepository.class);
        service = new PlanAccessService(goalRepository, planRepository);

        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        goalId = UUID.randomUUID();
        planId = UUID.randomUUID();

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        goal = mock(Goal.class);
        when(goal.getOwner()).thenReturn(owner);
        plan = mock(Plan.class);
        when(plan.getGoal()).thenReturn(goal);
    }

    @Test
    @DisplayName("requireGoalOwner 는 소유자면 목표를 돌려준다")
    void requireGoalOwnerReturnsOwnGoal() {
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        assertThat(service.requireGoalOwner(ownerId, goalId)).isSameAs(goal);
    }

    @Test
    @DisplayName("requireGoalOwner 는 남의 목표면 404")
    void requireGoalOwnerRejectsForeignGoal() {
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> service.requireGoalOwner(otherUserId, goalId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("목표를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("requireGoalOwner 는 목표가 없으면 남의 목표일 때와 같은 404")
    void requireGoalOwnerRejectsMissingGoalIdentically() {
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireGoalOwner(ownerId, goalId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("목표를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("requirePlanOwner 는 계획 → 목표 → 소유자를 거슬러 확인한다")
    void requirePlanOwnerReturnsOwnPlan() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThat(service.requirePlanOwner(ownerId, planId)).isSameAs(plan);
    }

    @Test
    @DisplayName("requirePlanOwner 는 남의 계획이면 404")
    void requirePlanOwnerRejectsForeignPlan() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.requirePlanOwner(otherUserId, planId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("계획을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("requirePlanOwner 는 계획이 없으면 남의 계획일 때와 같은 404")
    void requirePlanOwnerRejectsMissingPlanIdentically() {
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requirePlanOwner(ownerId, planId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("계획을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("생성자는 의존을 null 로 받지 않는다")
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new PlanAccessService(null, planRepository))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlanAccessService(goalRepository, null))
                .isInstanceOf(NullPointerException.class);
    }
}
