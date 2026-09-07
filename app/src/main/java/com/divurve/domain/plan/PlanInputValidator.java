package com.divurve.domain.plan;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.engine.planner.Cadence;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 계획 생성 전 입력 검증 (플래너 명세 §8).
 *
 * <p>명세 §8 의 마지막 문장이 이 클래스의 설계를 정한다 — <b>"검증 실패 시 계획을 임의로
 * 보정하지 않고 사용자가 수정해야 할 필드를 반환한다."</b> 그래서 어떤 값도 잘라내거나 기본값으로
 * 대체하지 않고, 문제가 된 필드명을 담아 400 을 던진다.
 *
 * <p>배정 가능한 보유 외화를 넘지 않는지(§8)와 여러 목표 중복 배정(§21-7)은 다른 목표의 배정액을
 * 알아야 하므로 {@link PlanAllocationGuard} 가 맡는다. 여기서는 <b>입력 자체로 판정할 수 있는
 * 것</b>만 본다.
 */
public final class PlanInputValidator {

    private PlanInputValidator() {
    }

    /**
     * 계획 계산 입력을 검증한다.
     *
     * @param input 목표 조건
     * @param today 오늘 (마감형 목표일 비교 기준)
     * @throws InvalidRequestException 검증에 실패한 경우. 수정해야 할 필드가 담긴다
     */
    public static void validate(PlanInput input, LocalDate today) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(today, "today");

        requireNonNegative(input.allocatedHoldingAmount(), "allocated_holding_amount");
        requireCurrencyCode(input.currencyCode());

        if (input.isRecurring()) {
            validateRecurring(input);
        } else {
            validateDeadline(input, today);
        }
    }

    /** 마감형 — 목표 금액과 목표일이 필수이고, 목표일은 미래여야 한다 (명세 §5.2·§8). */
    private static void validateDeadline(PlanInput input, LocalDate today) {
        requireNonNegative(input.targetAmount(), "target_amount");
        if (input.targetAmount() <= 0) {
            throw new InvalidRequestException("목표 외화 금액을 입력해 주세요.", "target_amount");
        }
        if (input.targetDate() == null) {
            throw new InvalidRequestException("목표일을 입력해 주세요.", "target_date");
        }
        if (!input.targetDate().isAfter(today)) {
            throw new InvalidRequestException(
                    "목표일은 오늘 이후여야 합니다: " + input.targetDate(), "target_date");
        }
        if (input.budgetAmountKrw() != null) {
            requireNonNegativeBudget(input.budgetAmountKrw());
            if (input.budgetPeriod() == null || input.budgetPeriod().isBlank()) {
                // 명세 §5.2 — 예산을 입력했으면 적용 주기는 필수다. 주기를 모르면
                // "목표일까지 쓸 수 있는 예산"을 계산할 수 없다.
                throw new InvalidRequestException(
                        "예산을 입력하면 예산 적용 주기도 함께 지정해야 합니다.", "budget_period");
            }
            requireCadence(input.budgetPeriod(), "budget_period");
        }
        if (input.cadence() != null && !input.cadence().isBlank()) {
            requireCadence(input.cadence(), "preferred_cadence");
        }
    }

    /** 정기형 — 회차 예산·주기·시작일·점검 기간이 전부 필수다 (명세 §5.3·§8). */
    private static void validateRecurring(PlanInput input) {
        if (input.budgetAmountKrw() == null || input.budgetAmountKrw() <= 0) {
            throw new InvalidRequestException(
                    "회차에 사용할 원화 예산을 입력해 주세요.", "recurring_budget_amount");
        }
        if (input.cadence() == null || input.cadence().isBlank()) {
            throw new InvalidRequestException("반복 주기를 선택해 주세요.", "recur_interval");
        }
        requireCadence(input.cadence(), "recur_interval");
        if (input.startDate() == null) {
            throw new InvalidRequestException("첫 계획 시작일을 입력해 주세요.", "start_date");
        }
        if (input.reviewHorizonMonths() == null || input.reviewHorizonMonths() < 1) {
            throw new InvalidRequestException(
                    "점검 기간은 1개월 이상이어야 합니다.", "review_horizon_months");
        }
    }

    private static void requireNonNegative(double amount, String field) {
        if (amount < 0) {
            throw new InvalidRequestException("금액은 0 이상이어야 합니다.", field);
        }
    }

    private static void requireNonNegativeBudget(long budgetKrw) {
        if (budgetKrw < 0) {
            throw new InvalidRequestException("예산은 0 이상이어야 합니다.", "budget_amount");
        }
    }

    private static void requireCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new InvalidRequestException("준비할 외화를 선택해 주세요.", "currency_code");
        }
    }

    /** 주기 코드가 해석 가능한지 확인한다. engine 의 판정을 그대로 쓰되 400 으로 바꾼다. */
    private static void requireCadence(String cadence, String field) {
        try {
            Cadence.from(cadence);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage(), field);
        }
    }
}
