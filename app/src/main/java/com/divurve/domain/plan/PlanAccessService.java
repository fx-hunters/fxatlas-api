package com.divurve.domain.plan;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계획 자원의 소유자 검증 (이슈 #50, NFR-SE-03).
 *
 * <p>이슈 #50 이전의 {@code PlanController} 에는 <b>소유자 검증이 전혀 없었다</b> —
 * {@code goalId} 나 {@code planId} 만 알면 남의 계획을 읽고 회차를 완료·건너뛸 수 있었다.
 *
 * <p>소유자가 아닌 경우와 자원이 없는 경우를 <b>모두 404</b> 로 동일하게 처리한다.
 * 403 으로 구분하면 "그 id 는 존재한다"는 사실이 새어나가기 때문이다
 * ({@code GoalService.getByIdAndOwner} 와 같은 방침).
 */
@UseCase
public class PlanAccessService {

    private static final String GOAL_NOT_FOUND = "목표를 찾을 수 없습니다.";
    private static final String PLAN_NOT_FOUND = "계획을 찾을 수 없습니다.";

    private final GoalRepository goalRepository;
    private final PlanRepository planRepository;

    public PlanAccessService(GoalRepository goalRepository, PlanRepository planRepository) {
        this.goalRepository = Objects.requireNonNull(goalRepository, "goalRepository");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository");
    }

    /**
     * 목표가 요청 주체의 것인지 확인한다.
     *
     * @throws NotFoundException 목표가 없거나 소유자가 아닌 경우
     */
    @Transactional(readOnly = true)
    public Goal requireGoalOwner(UUID ownerId, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new NotFoundException(GOAL_NOT_FOUND));
        if (!goal.getOwner().getId().equals(ownerId)) {
            throw new NotFoundException(GOAL_NOT_FOUND);
        }
        return goal;
    }

    /**
     * 계획이 요청 주체의 것인지 확인한다. 계획 → 목표 → 소유자 순으로 거슬러 올라간다.
     *
     * @throws NotFoundException 계획이 없거나 그 계획의 목표 소유자가 아닌 경우
     */
    @Transactional(readOnly = true)
    public Plan requirePlanOwner(UUID ownerId, UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException(PLAN_NOT_FOUND));
        if (!plan.getGoal().getOwner().getId().equals(ownerId)) {
            throw new NotFoundException(PLAN_NOT_FOUND);
        }
        return plan;
    }
}
