package com.divurve.domain.goal;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표 CRUD 유스케이스 (이슈 #17, FR-RT-01/02/03/04/05).
 * 소유자 필터(NFR-SE-03)로 데이터를 격리하고, held_amount는 HoldingService/DepositService에서 조회한다.
 */
@UseCase
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final HoldingService holdingService;
    private final DepositService depositService;

    public GoalService(
            GoalRepository goalRepository,
            UserRepository userRepository,
            HoldingService holdingService,
            DepositService depositService) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.holdingService = holdingService;
        this.depositService = depositService;
    }

    /** 소유자의 목표 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<Goal> listByOwner(UUID ownerId) {
        return goalRepository.findByOwner_Id(ownerId);
    }

    /** 새 목표를 생성한다. */
    @Transactional
    public Goal create(UUID ownerId, String name, String kind, String purpose, String currencyCode,
            double targetAmount, LocalDate targetDate, String recurInterval,
            long budgetAmount, String budgetCurrencyCode, String budgetPeriod, boolean isSpeculative) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        Goal goal = Goal.builder(owner, name, kind, purpose, currencyCode)
                .targetAmount(targetAmount)
                .targetDate(targetDate)
                .recurInterval(recurInterval)
                .budgetAmount(budgetAmount)
                .budgetCurrencyCode(budgetCurrencyCode)
                .budgetPeriod(budgetPeriod)
                .isSpeculative(isSpeculative)
                .status("active")
                .build();

        return goalRepository.save(goal);
    }

    /** 소유자의 목표를 조회한다. */
    @Transactional(readOnly = true)
    public Goal getByIdAndOwner(UUID ownerId, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new NotFoundException("목표를 찾을 수 없습니다."));
        if (!goal.getOwner().getId().equals(ownerId)) {
            throw new NotFoundException("목표를 찾을 수 없습니다.");
        }
        return goal;
    }

    /** 소유자의 목표를 수정한다. */
    @Transactional
    public Goal update(UUID ownerId, UUID goalId, String name, Double targetAmount,
            LocalDate targetDate, Long budgetAmount, String budgetPeriod, Boolean isSpeculative) {
        Goal goal = getByIdAndOwner(ownerId, goalId);

        if (name != null) {
            goal.setName(name);
        }
        if (targetAmount != null) {
            goal.setTargetAmount(targetAmount);
        }
        if (targetDate != null) {
            goal.setTargetDate(targetDate);
        }
        if (budgetAmount != null) {
            goal.setBudgetAmount(budgetAmount);
        }
        if (budgetPeriod != null) {
            goal.setBudgetPeriod(budgetPeriod);
        }
        if (isSpeculative != null) {
            goal.setSpeculative(isSpeculative);
        }

        return goal;
    }

    /** 소유자의 목표를 삭제한다. 계획 이력은 보존된다. */
    @Transactional
    public void delete(UUID ownerId, UUID goalId) {
        Goal goal = getByIdAndOwner(ownerId, goalId);
        goalRepository.delete(goal);
    }

    /** 소유자의 보유 외화금액을 조회한다 (목표금액과의 차이 계산에 사용). */
    @Transactional(readOnly = true)
    public double getHeldAmountByCurrency(UUID ownerId, String currencyCode) {
        double holdingAmount = calculateHoldingAmount(ownerId, currencyCode);
        double depositAmount = calculateDepositAmount(ownerId, currencyCode);
        return holdingAmount + depositAmount;
    }

    private double calculateHoldingAmount(UUID ownerId, String currencyCode) {
        return holdingService.list(ownerId).stream()
                .filter(holding -> currencyCode.equals(holding.getCurrencyCode()))
                .mapToDouble(holding -> holding.getQuantity() * holding.getAvgPrice())
                .sum();
    }

    private double calculateDepositAmount(UUID ownerId, String currencyCode) {
        return depositService.list(ownerId).stream()
                .filter(deposit -> currencyCode.equals(deposit.getCurrencyCode()))
                .map(deposit -> deposit.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }
}
