package com.divurve.domain.plan;

import com.divurve.domain.goal.GoalType;
import com.divurve.domain.goal.entity.Goal;
import java.time.LocalDate;

/**
 * 계획 계산에 필요한 목표 조건 (플래너 명세 §5).
 *
 * <p>{@link Goal} 엔티티가 아니라 별도 값으로 받는 이유는 <b>목표를 저장하기 전에도 계획을
 * 미리보기</b>할 수 있어야 하기 때문이다 — 명세 §12 의 장면 3·4 는 사용자가 조건을 확인하고
 * "이 조건으로 계획 만들기"를 누르기 <b>전에</b> 현재 위치와 회차를 보여준다.
 *
 * @param goalType                마감형/정기형 (명세 §4)
 * @param purpose                 목적 — 마감 버퍼 산출에 쓴다 (명세 §9.4)
 * @param currencyCode            준비할 외화
 * @param allocatedHoldingAmount  이 목표에 배정한 보유 외화 {@code H} (명세 §9.1)
 * @param targetAmount            마감형 목표 외화 총액 {@code T}. 정기형은 무시된다
 * @param targetDate              마감형 목표일. 정기형은 {@code null}
 * @param budgetAmountKrw         마감형의 회차·월 예산, 정기형의 회차 예산 (원). 마감형은 {@code null} 가능
 * @param budgetPeriod            마감형 예산 적용 주기 (명세 §5.2)
 * @param cadence                 마감형 준비 주기 / 정기형 반복 주기
 * @param startDate               정기형 첫 계획 시작일 (명세 §5.3)
 * @param reviewHorizonMonths     정기형 점검 기간 (명세 §5.3)
 */
public record PlanInput(
        String goalType,
        String purpose,
        String currencyCode,
        double allocatedHoldingAmount,
        double targetAmount,
        LocalDate targetDate,
        Long budgetAmountKrw,
        String budgetPeriod,
        String cadence,
        LocalDate startDate,
        Integer reviewHorizonMonths) {

    /** 저장된 목표에서 계산 입력을 만든다 (계획 확정·시나리오 재계산 경로). */
    public static PlanInput from(Goal goal) {
        return new PlanInput(
                goal.getGoalType(),
                goal.getPurpose(),
                goal.getCurrencyCode(),
                goal.getAllocatedHoldingAmount(),
                goal.getTargetAmount(),
                goal.getTargetDate(),
                goal.getBudgetAmount() > 0 ? goal.getBudgetAmount() : null,
                goal.getBudgetPeriod(),
                goal.isRecurring() ? goal.getRecurInterval() : goal.getPreferredCadence(),
                goal.getRecurStartDate(),
                goal.getReviewHorizonMonths());
    }

    /** 정기형 목표인지 — 회차 생성 방식과 Curve 의 마지막 노드가 갈린다 (명세 §4·§10). */
    public boolean isRecurring() {
        return GoalType.RECURRING.equals(goalType);
    }
}
