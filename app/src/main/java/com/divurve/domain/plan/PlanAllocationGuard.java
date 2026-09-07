package com.divurve.domain.plan;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.GoalService;
import com.divurve.domain.goal.entity.Goal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 외화 배정 검증 (플래너 명세 §8, 불변조건 §21-7).
 *
 * <p>명세가 두 가지를 요구한다.
 * <ul>
 *   <li>{@code allocatedHoldingAmount} 가 실제 배정 가능한 보유 외화를 넘지 않는지</li>
 *   <li><b>동일 보유 외화가 여러 목표에 중복 배정되지 않았는지</b> (§21-7)</li>
 * </ul>
 *
 * <p>둘은 사실 같은 검사다 — 다른 목표들이 이미 가져간 몫을 빼고 남은 것이 이 목표가 배정할 수
 * 있는 최대치다. 목표별로 따로 보면 각각은 보유량 이하인데 합치면 보유량을 넘는 상태를 놓친다.
 * 그러면 사용자는 실제로 없는 외화를 가진 것처럼 계획을 받게 된다.
 */
@UseCase
public class PlanAllocationGuard {

    private final GoalRepository goalRepository;
    private final GoalService goalService;

    public PlanAllocationGuard(GoalRepository goalRepository, GoalService goalService) {
        this.goalRepository = Objects.requireNonNull(goalRepository, "goalRepository");
        this.goalService = Objects.requireNonNull(goalService, "goalService");
    }

    /**
     * 배정 가능 여부를 검증한다.
     *
     * @param ownerId          소유자
     * @param currencyCode     목표 통화
     * @param requestedAmount  이 목표에 배정하려는 금액
     * @param excludedGoalId   합산에서 제외할 목표 (기존 목표의 배정액을 바꾸는 경우). 신규는 {@code null}
     * @throws InvalidRequestException 보유 외화를 넘겨 배정한 경우
     */
    @Transactional(readOnly = true)
    public void requireAllocatable(
            UUID ownerId, String currencyCode, double requestedAmount, UUID excludedGoalId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(currencyCode, "currencyCode");

        if (requestedAmount <= 0) {
            return;
        }

        double held = goalService.getHeldAmountByCurrency(ownerId, currencyCode);
        double allocatedElsewhere = allocatedElsewhere(ownerId, currencyCode, excludedGoalId);
        double allocatable = held - allocatedElsewhere;

        if (requestedAmount > allocatable) {
            throw new InvalidRequestException(
                    "배정 가능한 보유 외화를 넘었습니다. 보유 " + held + " " + currencyCode
                            + " 중 다른 목표에 " + allocatedElsewhere + " 이 이미 배정되어 있어 "
                            + allocatable + " 까지 배정할 수 있습니다.",
                    "allocated_holding_amount");
        }
    }

    private double allocatedElsewhere(UUID ownerId, String currencyCode, UUID excludedGoalId) {
        return goalRepository.findByOwner_Id(ownerId).stream()
                .filter(goal -> currencyCode.equals(goal.getCurrencyCode()))
                .filter(goal -> !goal.getId().equals(excludedGoalId))
                .mapToDouble(Goal::getAllocatedHoldingAmount)
                .sum();
    }
}
