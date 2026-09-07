package com.divurve.api.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 계획 계산 요청 (플래너 명세 §5 "사용자에게 직접 입력받는 데이터").
 *
 * <p>{@code goal_id} 는 <b>선택</b>이다 — 명세 §12 의 장면 3·4 는 목표를 저장하기 전에 조건을
 * 확인하고 계획을 미리 보여준다. {@code goal_id} 를 주면 저장된 목표의 조건을 쓰고, 주지 않으면
 * 요청 본문의 조건으로 계산한다.
 *
 * <p>필드가 유형별로 갈린다 — 마감형은 {@code target_amount}·{@code target_date} 가, 정기형은
 * {@code recurring_budget_amount}·{@code recur_interval}·{@code start_date}·
 * {@code review_horizon_months} 가 필수다. 어느 쪽이 빠졌는지는 검증이 필드명으로 알려준다
 * (명세 §8 — 임의로 보정하지 않는다).
 *
 * @param goalId                 저장된 목표 ID. 없으면 아래 조건으로 계산한다
 * @param goalType               {@code deadline} / {@code recurring} (명세 §4)
 * @param purpose                목적 — 마감 버퍼가 달라진다 (명세 §9.4)
 * @param currencyCode           준비할 외화
 * @param allocatedHoldingAmount 이 목표에 배정할 보유 외화 (명세 §5.1)
 * @param targetAmount           마감형 목표 외화 총액
 * @param targetDate             마감형 목표일
 * @param budgetAmount           마감형 예산 (원). 선택
 * @param budgetPeriod           마감형 예산 적용 주기. 예산을 넣으면 필수
 * @param preferredCadence       마감형 준비 주기. 없으면 주간
 * @param recurringBudgetAmount  정기형 회차 예산 (원)
 * @param recurInterval          정기형 반복 주기
 * @param startDate              정기형 첫 계획 시작일
 * @param reviewHorizonMonths    정기형 점검 기간 (개월)
 */
@Schema(description = "계획 계산 요청 (플래너 명세 §5)")
public record PlanRequest(
        String goalId,
        String goalType,
        String purpose,
        @NotBlank(message = "currency_code 는 필수입니다.") String currencyCode,
        @PositiveOrZero(message = "allocated_holding_amount 는 0 이상이어야 합니다.")
        double allocatedHoldingAmount,
        @PositiveOrZero(message = "target_amount 는 0 이상이어야 합니다.") double targetAmount,
        LocalDate targetDate,
        Long budgetAmount,
        String budgetPeriod,
        String preferredCadence,
        Long recurringBudgetAmount,
        String recurInterval,
        LocalDate startDate,
        Integer reviewHorizonMonths) {
}
