package com.divurve.domain.plan;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import com.divurve.engine.plan.PlanCalculator;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 계획 확정·저장·버전 관리 서비스 — <b>우선순위 P(구조만 준비)</b>.
 * 계획의 메타정보(safe_ratio, split_count 등)를 받아 저장하고,
 * 회차 목록을 생성한 후 버전 이력을 관리한다.
 *
 * <p><b>⚠ 요구사항 v2 §4.12 미확정 — 값은 후보이며 확정 요구사항이 아니다.</b> 안전/기회 버킷의
 * 존재와 비율 · 목적별 하한선 · 권장 분할 회차 · 몬테카를로 적용 여부 · 달성 확률 정의가 전부
 * 미확정이고, 기존 문서의 50/70/85/95% 와 4~8회는 후보값이다. API 명세 v2 §6 은 Route 계산
 * 엔드포인트를 명세하지 않으므로, {@code route.enabled} 가 꺼진 기본 상태에서 이 서비스는
 * 호출되지 않는다 — {@code PlanController} 가 진입 전에 501 로 막는다.
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
     * 계획 메타정보(safe_ratio, split_count 등)만 확정·저장한다. 회차는 저장하지 않는다 —
     * 회차까지 함께 저장하려면 {@link #confirmAndSaveWithSteps} 를 쓴다.
     * 기존 활성 계획이 있으면 비활성화하고, 새 버전을 활성화한다.
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
     * 계획을 확정·저장하고, 안전 버킷 금액을 균등분할한 회차를 함께 저장한다.
     * 회차 금액·스케줄은 {@link PlanCalculator#generateEqualSplitSchedule} 의 균등분할 공식을 쓴다
     * (계획 미리보기와 같은 공식 — 확정 결과가 미리보기와 어긋나지 않는다).
     *
     * @param goalId            목표 ID
     * @param weeklyBudgetKrw   주간 예산 (KRW) — 안전 버킷 금액 산출에 쓰인다
     * @param safeRatio         안전 버킷 비율 (0.0 ~ 1.0)
     * @param splitCount        회차 분할수 (1 이상)
     * @param opportunityAmount 기회 버킷 금액
     * @param triggerRate       기회 버킷 트리거 환율
     * @param changeReason      버전 변경 사유 (선택)
     * @return 저장된 계획 (회차까지 저장 완료)
     * @throws InvalidRequestException splitCount 가 1 미만인 경우
     */
    public Plan confirmAndSaveWithSteps(
            UUID goalId,
            long weeklyBudgetKrw,
            double safeRatio,
            int splitCount,
            double opportunityAmount,
            double triggerRate,
            String changeReason) {
        if (splitCount < 1) {
            throw new InvalidRequestException("split_count는 1 이상이어야 합니다: " + splitCount, "split_count");
        }

        Plan plan = confirmAndSavePlan(goalId, safeRatio, splitCount, opportunityAmount, triggerRate, changeReason);
        savePlanSteps(plan.getId(), buildStepInputs(plan, weeklyBudgetKrw, safeRatio, splitCount));
        return plan;
    }

    private List<StepInput> buildStepInputs(Plan plan, long weeklyBudgetKrw, double safeRatio, int splitCount) {
        long monthlyBudgetKrw = weeklyBudgetKrw * 4;
        double safeAmountKrw = monthlyBudgetKrw * safeRatio;
        int intervalDays = resolveIntervalDays(plan.getGoal().getRecurInterval(), splitCount);

        return PlanCalculator
                .generateEqualSplitSchedule(safeAmountKrw, splitCount, intervalDays, LocalDate.now())
                .stream()
                .map(step -> new StepInput(step.seq(), step.scheduledDate(), step.amount()))
                .toList();
    }

    /**
     * 목표의 반복주기를 회차 간격(일)으로 변환한다. 없으면 1년을 splitCount 로 균등 배분한다.
     * (계획 미리보기 {@code PlanPreviewService#calculateIntervalDays} 와 동일한 매핑 — 확정 시에도
     * 같은 간격 규칙을 써야 미리보기와 확정 스케줄이 어긋나지 않는다.)
     */
    private int resolveIntervalDays(String recurInterval, int splitCount) {
        if (recurInterval == null || recurInterval.isBlank()) {
            return 365 / splitCount;
        }

        return switch (recurInterval) {
            case "WEEKLY" -> 7;
            case "BIWEEKLY" -> 14;
            case "MONTHLY" -> 30;
            case "QUARTERLY" -> 90;
            default -> 365 / splitCount;
        };
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
