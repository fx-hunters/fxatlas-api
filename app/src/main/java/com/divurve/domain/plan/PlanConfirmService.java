package com.divurve.domain.plan;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 계획 확정·저장·버전 관리 서비스.
 * 계획의 메타정보(safe_ratio, split_count 등)를 받아 저장하고,
 * 회차 목록을 생성한 후 버전 이력을 관리한다.
 */
@UseCase
public class PlanConfirmService {

    private final GoalRepository goalRepository;
    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;

    public PlanConfirmService(
            GoalRepository goalRepository,
            PlanRepository planRepository,
            PlanStepRepository planStepRepository) {
        this.goalRepository = requireNonNull(goalRepository, "goalRepository");
        this.planRepository = requireNonNull(planRepository, "planRepository");
        this.planStepRepository = requireNonNull(planStepRepository, "planStepRepository");
    }

    /**
     * 계획을 확정·저장한다.
     * 기존 활성 계획이 있으면 비활성화하고, 새 버전을 활성화한다.
     * 계획 생성 시 회차는 저장되지 않는다 (클라이언트가 별도로 계산).
     * 단, 새로운 계획이 생성되면 이전 계획의 회차를 복사하거나 새로 생성할 수 있다.
     *
     * @param goalId            목표 ID
     * @param safeRatio         안전 버킷 비율 (0.0 ~ 1.0)
     * @param splitCount        회차 분할수
     * @param opportunityAmount 기회 버킷 금액
     * @param triggerRate       기회 버킷 트리거 환율
     * @param changeReason      버전 변경 사유 (선택)
     * @return 저장된 계획
     */
    public Plan confirmAndSavePlan(
            UUID goalId,
            double safeRatio,
            int splitCount,
            double opportunityAmount,
            double triggerRate,
            String changeReason) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        // 새 버전 번호 결정
        int newVersion = planRepository.findTopByGoal_IdOrderByVersionDesc(goalId)
                .map(p -> p.getVersion() + 1)
                .orElse(1);

        // 기존 활성 계획 비활성화
        planRepository.findByGoal_IdAndIsActiveTrue(goalId)
                .ifPresent(Plan::deactivate);

        // 새 계획 생성
        Plan newPlan = Plan.builder(goal, newVersion)
                .isActive(true)
                .reason(changeReason)
                .safeRatio(safeRatio)
                .splitCount(splitCount)
                .opportunityAmount(opportunityAmount)
                .opportunityTriggerRate(triggerRate)
                .build();

        return planRepository.save(newPlan);
    }

    /**
     * 계획의 회차를 생성하고 저장한다.
     * 회차는 seq 순서로 저장되며, 각 회차는 scheduled_date와 amount를 가진다.
     *
     * @param planId    계획 ID
     * @param steps     회차 정보 리스트 (seq, scheduledDate, amount)
     */
    public void savePlanSteps(UUID planId, List<StepInput> steps) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        for (StepInput step : steps) {
            PlanStep planStep = PlanStep.create(
                    plan,
                    step.seq(),
                    step.scheduledDate(),
                    step.amount(),
                    0.0, // 초기 executed_amount는 0
                    PlanStepStatus.PENDING);
            planStepRepository.save(planStep);
        }
    }

    /**
     * 계획의 회차 입력 정보.
     */
    public record StepInput(
            int seq,
            LocalDate scheduledDate,
            double amount) {
    }
}
