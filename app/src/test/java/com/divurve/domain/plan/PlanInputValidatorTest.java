package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.goal.GoalType;
import java.time.LocalDate;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanInputValidator} · {@link PlanInput} — 계획 생성 전 입력 검증 (플래너 명세 §8).
 *
 * <p>명세 §8 의 마지막 문장이 검증의 성격을 정한다 — <b>"검증 실패 시 계획을 임의로 보정하지 않고
 * 사용자가 수정해야 할 필드를 반환한다."</b> 그래서 모든 실패 케이스가 <b>어떤 필드</b>를 가리키는지
 * 함께 확인한다. 필드 없이 400 만 내면 사용자는 무엇을 고쳐야 할지 알 수 없다.
 */
@DisplayName("PlanInputValidator")
class PlanInputValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    private static PlanInput deadline() {
        return new PlanInput(
                GoalType.DEADLINE, "travel", "USD", 1000.0, 5000.0,
                LocalDate.of(2026, 12, 24), null, null, "weekly", null, null);
    }

    private static PlanInput recurring() {
        return new PlanInput(
                GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                500_000L, null, "monthly", LocalDate.of(2026, 10, 1), 6);
    }

    private static void assertRejects(ThrowingCallable call, String field) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("field", field);
    }

    @Test
    @DisplayName("null 인자는 거부한다")
    void nullArguments_Throw() {
        assertThatThrownBy(() -> PlanInputValidator.validate(null, TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PlanInputValidator.validate(deadline(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("공통")
    class Common {

        @Test
        @DisplayName("배정 외화가 음수면 거부한다")
        void negativeAllocation() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", -1.0, 5000.0,
                    LocalDate.of(2026, 12, 24), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "allocated_holding_amount");
        }

        @Test
        @DisplayName("통화가 비면 거부한다")
        void blankCurrency() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "  ", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "currency_code");
        }

        @Test
        @DisplayName("통화가 null 이면 거부한다")
        void nullCurrency() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", null, 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "currency_code");
        }
    }

    @Nested
    @DisplayName("마감형 (명세 §5.2)")
    class Deadline {

        @Test
        @DisplayName("금액·목표일이 있으면 통과한다")
        void valid() {
            assertThatCode(() -> PlanInputValidator.validate(deadline(), TODAY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("목표 금액이 음수면 거부한다")
        void negativeTarget() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, -1.0,
                    LocalDate.of(2026, 12, 24), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "target_amount");
        }

        @Test
        @DisplayName("목표 금액이 0이면 거부한다 — 준비할 것이 없는 계획은 만들 수 없다")
        void zeroTarget() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 0.0,
                    LocalDate.of(2026, 12, 24), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "target_amount");
        }

        @Test
        @DisplayName("목표일이 없으면 거부한다")
        void missingTargetDate() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    null, null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "target_date");
        }

        @Test
        @DisplayName("목표일이 과거면 거부한다 — 명세 §8")
        void pastTargetDate() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    TODAY.minusDays(1), null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "target_date");
        }

        @Test
        @DisplayName("목표일이 오늘이면 거부한다 — 회차를 하나도 만들 수 없다")
        void todayTargetDate() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    TODAY, null, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "target_date");
        }

        @Test
        @DisplayName("예산이 음수면 거부한다")
        void negativeBudget() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), -1L, "monthly", null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "budget_amount");
        }

        @Test
        @DisplayName("예산만 있고 주기가 없으면 거부한다 — 명세 §5.2")
        void budgetWithoutPeriod() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), 1_000_000L, null, null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "budget_period");
        }

        @Test
        @DisplayName("예산 주기가 비어 있어도 거부한다")
        void budgetWithBlankPeriod() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), 1_000_000L, " ", null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "budget_period");
        }

        @Test
        @DisplayName("예산 주기가 해석 불가면 거부한다")
        void unknownBudgetPeriod() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), 1_000_000L, "quarterly", null, null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "budget_period");
        }

        @Test
        @DisplayName("예산과 주기가 함께 있으면 통과한다")
        void budgetWithPeriod() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), 1_000_000L, "monthly", null, null, null);

            assertThatCode(() -> PlanInputValidator.validate(input, TODAY)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("준비 주기가 해석 불가면 거부한다")
        void unknownCadence() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), null, null, "daily", null, null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "preferred_cadence");
        }

        @Test
        @DisplayName("준비 주기를 비워도 통과한다 — 기본 주기를 쓴다")
        void blankCadenceIsAllowed() {
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 5000.0,
                    LocalDate.of(2026, 12, 24), null, null, " ", null, null);

            assertThatCode(() -> PlanInputValidator.validate(input, TODAY)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("정기형 (명세 §5.3)")
    class Recurring {

        @Test
        @DisplayName("예산·주기·시작일·점검 기간이 있으면 통과한다")
        void valid() {
            assertThatCode(() -> PlanInputValidator.validate(recurring(), TODAY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("회차 예산이 없으면 거부한다")
        void missingBudget() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    null, null, "monthly", LocalDate.of(2026, 10, 1), 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "recurring_budget_amount");
        }

        @Test
        @DisplayName("회차 예산이 0이면 거부한다")
        void zeroBudget() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    0L, null, "monthly", LocalDate.of(2026, 10, 1), 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "recurring_budget_amount");
        }

        @Test
        @DisplayName("반복 주기가 없으면 거부한다")
        void missingInterval() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, null, LocalDate.of(2026, 10, 1), 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "recur_interval");
        }

        @Test
        @DisplayName("반복 주기가 비어 있어도 거부한다")
        void blankInterval() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, " ", LocalDate.of(2026, 10, 1), 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "recur_interval");
        }

        @Test
        @DisplayName("반복 주기가 해석 불가면 거부한다")
        void unknownInterval() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, "daily", LocalDate.of(2026, 10, 1), 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "recur_interval");
        }

        @Test
        @DisplayName("시작일이 없으면 거부한다")
        void missingStartDate() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, "monthly", null, 6);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "start_date");
        }

        @Test
        @DisplayName("점검 기간이 없으면 거부한다")
        void missingHorizon() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, "monthly", LocalDate.of(2026, 10, 1), null);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "review_horizon_months");
        }

        @Test
        @DisplayName("점검 기간이 1개월 미만이면 거부한다")
        void horizonBelowOne() {
            PlanInput input = new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    500_000L, null, "monthly", LocalDate.of(2026, 10, 1), 0);

            assertRejects(() -> PlanInputValidator.validate(input, TODAY), "review_horizon_months");
        }

        @Test
        @DisplayName("정기형은 목표일을 요구하지 않는다 — 명세 §5.3")
        void targetDateNotRequired() {
            assertThatCode(() -> PlanInputValidator.validate(recurring(), TODAY))
                    .doesNotThrowAnyException();
        }
    }
}
