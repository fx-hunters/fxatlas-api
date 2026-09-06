package com.divurve.domain.plan;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import com.divurve.engine.plan.PlanCalculator;
import java.util.List;
import java.util.UUID;

/**
 * 계획 회차 실행(완료/건너뛰기) 서비스.
 * 회차 상태를 업데이트하고, 건너뛰기 시 남은 회차에 부담을 재분배한다.
 */
@UseCase
public class PlanStepExecutionService {

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;

    public PlanStepExecutionService(
            PlanRepository planRepository,
            PlanStepRepository planStepRepository) {
        this.planRepository = requireNonNull(planRepository, "planRepository");
        this.planStepRepository = requireNonNull(planStepRepository, "planStepRepository");
    }

    /**
     * 회차 완료 기록.
     *
     * @param planId          계획 ID
     * @param seq             회차번호
     * @param executedAmount  실제 체결된 외화 금액
     * @return 완료된 회차
     */
    public PlanStep completeStep(UUID planId, int seq, double executedAmount) {
        PlanStep step = planStepRepository.findByPlan_IdAndSeq(planId, seq)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PlanStep not found: planId=" + planId + ", seq=" + seq));

        step.markAsCompleted(executedAmount);
        return planStepRepository.save(step);
    }

    /**
     * 회차 건너뛰기.
     * 다음 회차부터 남은 금액을 남은 회차로 나누어 부담을 증가시킨다.
     *
     * @param planId      계획 ID
     * @param seq         건너뛸 회차번호
     * @param targetAmount 목표 외화 금액 (남은 금액 계산용)
     * @return 건너뛰기 결과 (연속 건너뛰기 수, 새로운 부담 등)
     */
    public SkipResult skipStep(UUID planId, int seq, double targetAmount) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        PlanStep currentStep = planStepRepository.findByPlan_IdAndSeq(planId, seq)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PlanStep not found: planId=" + planId + ", seq=" + seq));

        currentStep.markAsSkipped();
        planStepRepository.save(currentStep);

        // 현재까지 실행한 총 금액 계산
        List<PlanStep> allSteps = planStepRepository.findByPlan_IdOrderBySeqAsc(planId);
        double executedAmount = allSteps.stream()
                .filter(PlanStep::isCompleted)
                .mapToDouble(PlanStep::getExecutedAmount)
                .sum();

        // 남은 금액과 남은 회차 계산
        double remainingAmount = PlanCalculator.calculateRemainingAmount(targetAmount, executedAmount);
        int totalSteps = allSteps.size();
        int skippedCount = (int) allSteps.stream()
                .filter(PlanStep::isSkipped)
                .count() + 1; // 현재 회차 포함
        int remainingSteps = PlanCalculator.calculateRemainingSteps(totalSteps, seq + 1, skippedCount);

        // 다음 회차부터 부담 재분배
        if (remainingSteps > 0 && remainingAmount > 0) {
            double newAmount = remainingAmount / remainingSteps;
            for (PlanStep step : allSteps) {
                if (step.getSeq() > seq && step.isPending()) {
                    step.updateAmount(newAmount);
                    planStepRepository.save(step);
                }
            }
        }

        // 연속 건너뛰기 카운팅 (현재부터 역으로 카운트)
        int consecutiveSkips = countConsecutiveSkips(allSteps, seq);

        // 건너뛰기 전후 부담과 달성확률 변화 계산
        double burdenBefore = currentStep.getAmount();
        double nextStepBurden = remainingSteps > 0 && remainingAmount > 0
                ? remainingAmount / remainingSteps
                : 0.0;
        double burdenIncreasePct = burdenBefore > 0
                ? ((nextStepBurden - burdenBefore) / burdenBefore) * 100
                : 0.0;

        boolean safeModeTriggered = PlanCalculator.shouldTriggerSafeMode(consecutiveSkips);

        return new SkipResult(
                consecutiveSkips,
                burdenBefore,
                nextStepBurden,
                burdenIncreasePct,
                safeModeTriggered,
                remainingAmount,
                remainingSteps);
    }

    /**
     * seq 위치의 연속 건너뛰기 카운트.
     * seq부터 역으로 올라가면서 SKIPPED 상태가 연속되는 회차를 센다.
     */
    private int countConsecutiveSkips(List<PlanStep> allSteps, int seq) {
        int count = 1; // 현재 회차 포함
        for (int i = seq - 1; i >= 0; i--) {
            if (i < allSteps.size() && allSteps.get(i).isSkipped()) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 건너뛰기 결과.
     */
    public record SkipResult(
            int consecutiveSkips,
            double burdenBefore,
            double burdenAfter,
            double burdenIncreasePct,
            boolean safeModeTriggered,
            double remainingAmount,
            int remainingSteps) {
    }
}
