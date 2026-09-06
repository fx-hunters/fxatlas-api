package com.divurve.domain.plan;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
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
        // 계획 존재 여부 검증 (없는 계획의 회차는 건너뛸 수 없다)
        planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        PlanStep currentStep = planStepRepository.findByPlan_IdAndSeq(planId, seq)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PlanStep not found: planId=" + planId + ", seq=" + seq));

        // 재분배 전 현재 회차의 부담을 먼저 확보한다
        double burdenBefore = currentStep.getAmount();

        currentStep.markAsSkipped();
        planStepRepository.save(currentStep);

        List<PlanStep> allSteps = planStepRepository.findByPlan_IdOrderBySeqAsc(planId);

        // 현재까지 실행한 총 금액·회차수 집계 (현재 회차는 이미 SKIPPED 로 반영되어 있다)
        double executedAmount = allSteps.stream()
                .filter(PlanStep::isCompleted)
                .mapToDouble(PlanStep::getExecutedAmount)
                .sum();
        int completedCount = (int) allSteps.stream()
                .filter(PlanStep::isCompleted)
                .count();
        int skippedCount = (int) allSteps.stream()
                .filter(PlanStep::isSkipped)
                .count();

        double remainingAmount = PlanCalculator.calculateRemainingAmount(targetAmount, executedAmount);
        int remainingSteps = PlanCalculator.calculateRemainingSteps(
                allSteps.size(), completedCount, skippedCount);

        // 남은 회차(PENDING)에 남은 금액을 균등 재분배한다.
        // remainingSteps 는 PENDING 회차수와 같으므로 0 이면 재분배 대상도 없다.
        double burdenAfter = remainingSteps > 0 ? remainingAmount / remainingSteps : 0.0;
        for (PlanStep step : allSteps) {
            if (step.getSeq() > seq && step.isPending()) {
                step.updateAmount(burdenAfter);
                planStepRepository.save(step);
            }
        }

        double burdenIncreasePct = PlanCalculator.calculateBurdenIncreaseRatio(
                remainingAmount, remainingSteps, burdenBefore) * 100.0;

        int consecutiveSkips = countConsecutiveSkips(allSteps, seq);
        boolean safeModeTriggered = PlanCalculator.shouldTriggerSafeMode(consecutiveSkips);

        return new SkipResult(
                consecutiveSkips,
                burdenBefore,
                burdenAfter,
                burdenIncreasePct,
                safeModeTriggered,
                remainingAmount,
                remainingSteps);
    }

    /**
     * seq 위치의 연속 건너뛰기 카운트.
     * seq 직전 회차부터 역순으로 내려가며 SKIPPED 가 연속되는 동안 센다.
     * 현재 회차(seq)는 이미 건너뛴 상태이므로 1 부터 시작한다.
     */
    private int countConsecutiveSkips(List<PlanStep> allSteps, int seq) {
        int count = 1; // 현재 회차 포함
        for (int i = allSteps.size() - 1; i >= 0; i--) {
            PlanStep step = allSteps.get(i);
            if (step.getSeq() >= seq) {
                continue; // 현재 회차 및 이후 회차는 대상이 아니다
            }
            if (!step.isSkipped()) {
                break;
            }
            count++;
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
