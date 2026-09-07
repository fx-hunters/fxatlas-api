package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.goal.GoalType;
import com.divurve.engine.planner.BudgetFeasibilityEvaluator;
import com.divurve.engine.planner.BudgetState;
import com.divurve.engine.planner.BusinessDayCalendar;
import com.divurve.engine.planner.EqualSplitAllocator;
import com.divurve.engine.planner.ExchangeCostCalculator;
import com.divurve.engine.planner.PlannerPolicy;
import com.divurve.engine.planner.RecurringAcquisitionCalculator;
import com.divurve.engine.planner.RoundScheduleGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanCalculationService} — 마감형·정기형 계획 계산 (플래너 명세 §9·§10).
 *
 * <p>engine 계산기는 목이 아니라 <b>진짜 구현</b>을 쓴다. 이 서비스의 책임은 "어느 계산기를 어떤
 * 순서로 부르는가"이므로, 계산기를 목으로 바꾸면 정작 검증하려는 조합이 사라진다. 대신 환율
 * 전제만 목으로 고정해 결과를 결정적으로 만든다.
 */
@DisplayName("PlanCalculationService")
class PlanCalculationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    /** 2026-09-07 은 월요일 — 주간 회차가 매주 월요일에 떨어진다. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private PlanRateContextProvider rateContextProvider;
    private PlanCalculationService service;

    @BeforeEach
    void setUp() {
        rateContextProvider = mock(PlanRateContextProvider.class);
        ExchangeCostCalculator exchangeCostCalculator = new ExchangeCostCalculator();
        service = new PlanCalculationService(
                rateContextProvider,
                new BusinessDayCalendar(),
                new RoundScheduleGenerator(),
                new EqualSplitAllocator(),
                exchangeCostCalculator,
                new BudgetFeasibilityEvaluator(),
                new RecurringAcquisitionCalculator(exchangeCostCalculator),
                CLOCK);
    }

    /** 스프레드·수수료 0 인 전제 — 계산 결과를 손으로 검산할 수 있게 한다. */
    private void stubRates(String currency, double low, double base, double high, int minorUnits) {
        when(rateContextProvider.resolve(any(), anyString())).thenReturn(new PlanRateContext(
                currency, low, base, high, 0.0, 0L, 1, minorUnits,
                Instant.now(CLOCK), Instant.now(CLOCK), true));
    }

    private void stubRatesWithoutForecast(String currency, double rate) {
        when(rateContextProvider.resolve(any(), anyString())).thenReturn(new PlanRateContext(
                currency, rate, rate, rate, 0.0, 0L, 1, 2,
                Instant.now(CLOCK), null, false));
    }

    private PlanInput deadline(double target, double held, LocalDate targetDate, Long budget, String period) {
        return new PlanInput(
                GoalType.DEADLINE, "travel", "USD", held, target, targetDate,
                budget, period, "weekly", null, null);
    }

    @Nested
    @DisplayName("마감형 (명세 §9)")
    class Deadline {

        @Test
        @DisplayName("남은 외화는 목표에서 배정액을 뺀 값이다 — R = max(T - H, 0)")
        void remainingAmount() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(5000.0, 1000.0, TODAY.plusMonths(3), null, null));

            assertThat(draft.goal().remainingAmount()).isEqualByComparingTo("4000.00");
        }

        @Test
        @DisplayName("회차 금액의 합은 남은 외화와 정확히 같다 — 불변조건 §21-2")
        void stepAmountsSumToRemaining() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(5000.0, 1000.0, TODAY.plusMonths(3), null, null));

            BigDecimal sum = draft.steps().stream()
                    .map(PlanDraft.Step::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(draft.goal().remainingAmount());
        }

        @Test
        @DisplayName("잔여분은 마지막 회차에만 붙는다 — 불변조건 §21-3")
        void remainderGoesToLastStepOnly() {
            stubRates("USD", 1300, 1350, 1400, 2);

            // 1000 / 3회 = 333.333... → 앞 회차는 같고 마지막만 다르다
            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusDays(15), null, null));

            var amounts = draft.steps().stream().map(PlanDraft.Step::amount).toList();
            BigDecimal first = amounts.get(0);
            assertThat(amounts.subList(0, amounts.size() - 1))
                    .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(first));
            assertThat(amounts.get(amounts.size() - 1)).isGreaterThanOrEqualTo(first);
        }

        @Test
        @DisplayName("모든 회차는 계획 종료일 이전이다 — 불변조건 §21-4")
        void allStepsBeforePlanEndDate() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(5000.0, 0.0, TODAY.plusMonths(3), null, null));

            assertThat(draft.steps()).isNotEmpty().allSatisfy(step ->
                    assertThat(step.scheduledDate())
                            .isBeforeOrEqualTo(draft.summary().planEndDate()));
        }

        @Test
        @DisplayName("계획 종료일은 목적별 영업일 버퍼만큼 앞선다 — 명세 §9.4")
        void planEndDateAppliesBusinessDayBuffer() {
            stubRates("USD", 1300, 1350, 1400, 2);
            LocalDate targetDate = LocalDate.of(2026, 12, 24); // 목요일

            PlanDraft travel = service.calculate(
                    USER_ID, deadline(5000.0, 0.0, targetDate, null, null));
            PlanDraft tuition = service.calculate(USER_ID, new PlanInput(
                    GoalType.DEADLINE, "tuition", "USD", 0.0, 5000.0, targetDate,
                    null, null, "weekly", null, null));

            // 여행 3영업일 → 12/21(월), 학비 5영업일 → 12/17(목)
            assertThat(travel.summary().planEndDate()).isEqualTo(LocalDate.of(2026, 12, 21));
            assertThat(tuition.summary().planEndDate()).isEqualTo(LocalDate.of(2026, 12, 17));
        }

        @Test
        @DisplayName("비용은 환율 범위에 따라 달라진다 — 명세 §9.3")
        void costScalesWithRateRange() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusMonths(2), null, null));

            // 스프레드·수수료 0 이므로 1000 × 환율 그대로다
            assertThat(draft.summary().costRange().lowKrw()).isEqualTo(1_300_000L);
            assertThat(draft.summary().costRange().baseKrw()).isEqualTo(1_350_000L);
            assertThat(draft.summary().costRange().highKrw()).isEqualTo(1_400_000L);
        }

        @Test
        @DisplayName("예산이 상단 비용을 덮으면 COVERED_IN_RANGE 다")
        void budgetCoversHighCost() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID,
                    deadline(1000.0, 0.0, TODAY.plusDays(28), 500_000L, "weekly"));

            // 버퍼 3영업일을 빼면 9/30 까지 주간 4회 → 4 × 500,000 = 2,000,000 ≥ 1,400,000
            assertThat(draft.summary().budgetState())
                    .isEqualTo(BudgetState.COVERED_IN_RANGE.name());
        }

        @Test
        @DisplayName("예산이 범위 사이면 RANGE_SENSITIVE 다")
        void budgetBetweenRange() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID,
                    deadline(1000.0, 0.0, TODAY.plusDays(28), 340_000L, "weekly"));

            // 4회 × 340,000 = 1,360,000 → 1,300,000 이상 1,400,000 미만
            assertThat(draft.summary().budgetState())
                    .isEqualTo(BudgetState.RANGE_SENSITIVE.name());
        }

        @Test
        @DisplayName("예산이 하단에도 못 미치면 조정이 필요하고 경고를 낸다 — 명세 §9.6·§21-8")
        void budgetBelowLowCost() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID,
                    deadline(1000.0, 0.0, TODAY.plusDays(28), 100_000L, "weekly"));

            assertThat(draft.summary().budgetState())
                    .isEqualTo(BudgetState.CONSTRAINT_ADJUSTMENT_REQUIRED.name());
            assertThat(draft.warnings()).contains(PlanCalculationService.WARNING_BUDGET_SHORTFALL);
        }

        @Test
        @DisplayName("예산을 입력하지 않으면 가능 여부를 판정하지 않는다 — 명세 §9.6")
        void budgetNotProvided() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusMonths(2), null, null));

            assertThat(draft.summary().budgetState())
                    .isEqualTo(BudgetState.BUDGET_NOT_PROVIDED.name());
        }

        @Test
        @DisplayName("이미 목표를 채웠으면 회차를 만들지 않고 완료로 안내한다 — 명세 §9.2")
        void targetAlreadyMet() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 1500.0, TODAY.plusMonths(2), null, null));

            assertThat(draft.steps()).isEmpty();
            assertThat(draft.summary().status()).isEqualTo(PlanStatus.COMPLETED);
            assertThat(draft.summary().nextActionSeq()).isNull();
            assertThat(draft.warnings()).contains(PlanCalculationService.WARNING_TARGET_ALREADY_MET);
        }

        @Test
        @DisplayName("버퍼를 적용하면 회차를 못 만드는 목표일은 거부한다 — 명세 §8")
        void tooCloseTargetDate() {
            stubRates("USD", 1300, 1350, 1400, 2);

            assertThatThrownBy(() -> service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusDays(1), null, null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("field", "target_date");
        }

        @Test
        @DisplayName("주기를 지정하지 않으면 주간으로 만든다")
        void defaultCadenceIsWeekly() {
            stubRates("USD", 1300, 1350, 1400, 2);
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 1000.0, TODAY.plusDays(28),
                    null, null, null, null, null);

            PlanDraft draft = service.calculate(USER_ID, input);

            // 목표일 10/5 에서 여행 버퍼 3영업일을 빼면 9/30 — 9/7·14·21·28 네 회차다
            assertThat(draft.steps()).hasSize(4);
            assertThat(draft.steps().get(1).scheduledDate())
                    .isEqualTo(draft.steps().get(0).scheduledDate().plusWeeks(1));
        }

        @Test
        @DisplayName("주기를 공백으로 줘도 기본 주기를 쓴다")
        void blankCadenceFallsBackToWeekly() {
            stubRates("USD", 1300, 1350, 1400, 2);
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "USD", 0.0, 1000.0, TODAY.plusDays(28),
                    null, null, " ", null, null);

            assertThat(service.calculate(USER_ID, input).steps()).hasSize(4);
        }

        @Test
        @DisplayName("첫 회차가 다음 행동이다 — 명세 §11.3")
        void firstStepIsNextAction() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusMonths(2), null, null));

            assertThat(draft.summary().nextActionSeq()).isEqualTo(1);
            assertThat(draft.steps().get(0).nextAction()).isTrue();
            assertThat(draft.steps().get(1).nextAction()).isFalse();
        }

        @Test
        @DisplayName("Forecast 가 없으면 경고를 함께 낸다 — 명세 §20")
        void forecastUnavailableWarning() {
            stubRatesWithoutForecast("USD", 1350);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusMonths(2), null, null));

            assertThat(draft.warnings()).contains(PlanRateContext.WARNING_FORECAST_UNAVAILABLE);
            assertThat(draft.summary().costRange().lowKrw())
                    .isEqualTo(draft.summary().costRange().highKrw());
        }

        @Test
        @DisplayName("정책 버전을 결과에 남긴다 — 명세 §7·§11.1")
        void carriesPolicyVersion() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(
                    USER_ID, deadline(1000.0, 0.0, TODAY.plusMonths(2), null, null));

            assertThat(draft.policyVersion()).isEqualTo(PlannerPolicy.POLICY_VERSION);
            assertThat(draft.calculatedAt()).isEqualTo(Instant.now(CLOCK));
        }

        @Test
        @DisplayName("JPY 는 소수 없이 정수 단위로 분배한다 — 명세 §21-6")
        void jpyUsesWholeUnits() {
            stubRates("JPY", 9.0, 9.5, 10.0, 0);
            PlanInput input = new PlanInput(
                    GoalType.DEADLINE, "travel", "JPY", 0.0, 100000.0, TODAY.plusDays(21),
                    null, null, "weekly", null, null);

            PlanDraft draft = service.calculate(USER_ID, input);

            assertThat(draft.steps()).allSatisfy(step ->
                    assertThat(step.amount().scale()).isZero());
        }
    }

    @Nested
    @DisplayName("정기형 (명세 §10)")
    class Recurring {

        private PlanInput recurring(long budget, int months, String interval) {
            return new PlanInput(
                    GoalType.RECURRING, "investment", "USD", 0.0, 0.0, null,
                    budget, null, interval, TODAY, months);
        }

        @Test
        @DisplayName("점검 기간까지 주기별 회차를 만든다 — 명세 §10.1")
        void generatesRoundsUntilReviewEnd() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(500_000L, 6, "monthly"));

            assertThat(draft.steps()).hasSize(7);
            assertThat(draft.summary().planEndDate()).isEqualTo(TODAY.plusMonths(6));
        }

        @Test
        @DisplayName("환율이 높을수록 확보 외화가 줄어든다 — 명세 §10.2")
        void higherRateYieldsLessForeign() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(1_350_000L, 3, "monthly"));

            PlanDraft.AcquisitionRange range = draft.steps().get(0).acquisition();
            assertThat(range.low()).isLessThan(range.base());
            assertThat(range.base()).isLessThan(range.high());
            assertThat(range.base()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("회차 예산이 곧 비용이다 — 정기형은 지출이 고정된다 (명세 §10.3)")
        void budgetIsTheCost() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(500_000L, 3, "monthly"));

            assertThat(draft.steps().get(0).budgetKrw()).isEqualTo(500_000L);
            assertThat(draft.steps().get(0).costRange().lowKrw()).isEqualTo(500_000L);
            assertThat(draft.steps().get(0).costRange().highKrw()).isEqualTo(500_000L);
            assertThat(draft.summary().costRange().baseKrw())
                    .isEqualTo(500_000L * draft.steps().size());
        }

        @Test
        @DisplayName("점검 시점의 누적 확보 외화 범위를 낸다 — 명세 §10.3")
        void cumulativeAcquisition() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(1_350_000L, 3, "monthly"));

            PlanDraft.AcquisitionRange perRound = draft.steps().get(0).acquisition();
            PlanDraft.AcquisitionRange cumulative = draft.summary().cumulativeAcquisition();
            assertThat(cumulative.base())
                    .isEqualByComparingTo(perRound.base().multiply(BigDecimal.valueOf(draft.steps().size())));
        }

        @Test
        @DisplayName("정기형은 예산 가능 여부를 판정하지 않는다 — 예산이 곧 입력이다")
        void noBudgetState() {
            stubRates("USD", 1300, 1350, 1400, 2);

            assertThat(service.calculate(USER_ID, recurring(500_000L, 3, "monthly"))
                    .summary().budgetState()).isNull();
        }

        @Test
        @DisplayName("정기형 Curve 의 마지막 지점은 목표 도착이 아니라 다음 점검이다 — 명세 §4·§10.3")
        void endsAtReviewNotArrival() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(500_000L, 3, "monthly"));

            assertThat(draft.goal().targetAmount()).isNull();
            assertThat(draft.goal().targetDate()).isEqualTo(TODAY.plusMonths(3));
            assertThat(draft.goal().roundBudgetKrw()).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("주간 반복도 점검 기간까지 만든다")
        void weeklyRecurring() {
            stubRates("USD", 1300, 1350, 1400, 2);

            PlanDraft draft = service.calculate(USER_ID, recurring(100_000L, 1, "weekly"));

            assertThat(draft.steps()).hasSize(5);
        }
    }

    @Test
    @DisplayName("null 인자와 의존은 거부한다")
    void nullArguments_Throw() {
        assertThatThrownBy(() -> service.calculate(USER_ID, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlanCalculationService(
                null, new BusinessDayCalendar(), new RoundScheduleGenerator(),
                new EqualSplitAllocator(), new ExchangeCostCalculator(),
                new BudgetFeasibilityEvaluator(),
                new RecurringAcquisitionCalculator(new ExchangeCostCalculator()), CLOCK))
                .isInstanceOf(NullPointerException.class);
    }
}
