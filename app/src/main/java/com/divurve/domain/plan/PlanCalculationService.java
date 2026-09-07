package com.divurve.domain.plan;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.engine.planner.AcquisitionRange;
import com.divurve.engine.planner.BudgetFeasibilityEvaluator;
import com.divurve.engine.planner.BudgetState;
import com.divurve.engine.planner.BusinessDayCalendar;
import com.divurve.engine.planner.Cadence;
import com.divurve.engine.planner.CostRange;
import com.divurve.engine.planner.EqualSplitAllocator;
import com.divurve.engine.planner.ExchangeCostCalculator;
import com.divurve.engine.planner.PlannerPolicy;
import com.divurve.engine.planner.RecurringAcquisitionCalculator;
import com.divurve.engine.planner.RoundScheduleGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 계획 계산 UseCase (플래너 명세 §9 마감형 · §10 정기형).
 *
 * <p><b>저장하지 않는다.</b> 계산 결과인 {@link PlanDraft} 를 돌려줄 뿐이며, 영속화는
 * {@link PlanConfirmService} 가 사용자의 확정 요청을 받아 수행한다 — 명세 §12 는 조건 확인과
 * 계획 생성을 별도 장면으로 나누고, §18 은 미리보기가 활성 계획을 바꾸지 않도록 요구한다(§21-9).
 *
 * <p>모든 수치는 {@code engine/planner} 의 순수 함수가 만든다. 이 서비스는 <b>어떤 산술도 직접
 * 하지 않고</b> 목표 유형에 따라 어느 계산기를 어떤 순서로 부를지만 정한다.
 */
@UseCase
public class PlanCalculationService {

    /** 마감형에서 주기를 지정하지 않았을 때의 기본값 (명세 §5.2 {@code preferredCadence} 선택). */
    private static final Cadence DEFAULT_DEADLINE_CADENCE = Cadence.WEEKLY;

    private final PlanRateContextProvider rateContextProvider;
    private final BusinessDayCalendar businessDayCalendar;
    private final RoundScheduleGenerator roundScheduleGenerator;
    private final EqualSplitAllocator equalSplitAllocator;
    private final ExchangeCostCalculator exchangeCostCalculator;
    private final BudgetFeasibilityEvaluator budgetFeasibilityEvaluator;
    private final RecurringAcquisitionCalculator recurringAcquisitionCalculator;
    private final Clock clock;

    public PlanCalculationService(
            PlanRateContextProvider rateContextProvider,
            BusinessDayCalendar businessDayCalendar,
            RoundScheduleGenerator roundScheduleGenerator,
            EqualSplitAllocator equalSplitAllocator,
            ExchangeCostCalculator exchangeCostCalculator,
            BudgetFeasibilityEvaluator budgetFeasibilityEvaluator,
            RecurringAcquisitionCalculator recurringAcquisitionCalculator,
            Clock clock) {
        this.rateContextProvider = Objects.requireNonNull(rateContextProvider, "rateContextProvider");
        this.businessDayCalendar = Objects.requireNonNull(businessDayCalendar, "businessDayCalendar");
        this.roundScheduleGenerator = Objects.requireNonNull(roundScheduleGenerator, "roundScheduleGenerator");
        this.equalSplitAllocator = Objects.requireNonNull(equalSplitAllocator, "equalSplitAllocator");
        this.exchangeCostCalculator = Objects.requireNonNull(exchangeCostCalculator, "exchangeCostCalculator");
        this.budgetFeasibilityEvaluator =
                Objects.requireNonNull(budgetFeasibilityEvaluator, "budgetFeasibilityEvaluator");
        this.recurringAcquisitionCalculator =
                Objects.requireNonNull(recurringAcquisitionCalculator, "recurringAcquisitionCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 목표 조건으로 계획을 계산한다.
     *
     * @param userId 조회 사용자
     * @param input  목표 조건 (명세 §5)
     * @return 계산된 계획. 저장되지 않았다
     * @throws InvalidRequestException 입력 검증(§8)이나 환율 최신성(§20)에 걸린 경우
     */
    public PlanDraft calculate(UUID userId, PlanInput input) {
        Objects.requireNonNull(input, "input");
        LocalDate today = LocalDate.now(clock);
        PlanInputValidator.validate(input, today);

        PlanRateContext rateContext = rateContextProvider.resolve(userId, input.currencyCode());
        return input.isRecurring()
                ? calculateRecurring(input, rateContext, today)
                : calculateDeadline(input, rateContext, today);
    }

    // ── 마감형 (명세 §9) ───────────────────────────────────────────────────

    private PlanDraft calculateDeadline(PlanInput input, PlanRateContext rateContext, LocalDate today) {
        List<String> warnings = baseWarnings(rateContext);

        // §9.2 — R = max(T - H, 0)
        BigDecimal target = equalSplitAllocator.normalize(
                BigDecimal.valueOf(input.targetAmount()), rateContext.minorUnits());
        BigDecimal held = equalSplitAllocator.normalize(
                BigDecimal.valueOf(input.allocatedHoldingAmount()), rateContext.minorUnits());
        BigDecimal remaining = target.subtract(held).max(BigDecimal.ZERO);

        // §9.4 — planEndDate = targetDate - businessDayBuffer
        LocalDate planEndDate = businessDayCalendar.minusBusinessDays(
                input.targetDate(), PlannerPolicy.businessDayBufferFor(input.purpose()));

        if (remaining.signum() == 0) {
            // §9.2 — R 이 0 이면 신규 회차를 만들지 않고 목표 완료·수정·재배정을 안내한다.
            warnings.add(WARNING_TARGET_ALREADY_MET);
            return new PlanDraft(
                    Instant.now(clock), PlannerPolicy.POLICY_VERSION, rateContext,
                    deadlineGoalSummary(input, target, held, remaining),
                    new PlanDraft.Summary(
                            PlanStatus.COMPLETED, planEndDate, 0, 0, 0, 0, null,
                            new PlanDraft.CostRange(0L, 0L, 0L),
                            BudgetState.COVERED_IN_RANGE.name(), null),
                    List.of(), warnings);
        }

        Cadence cadence = resolveCadence(input.cadence(), DEFAULT_DEADLINE_CADENCE);
        List<LocalDate> dates = roundScheduleGenerator.generate(today, planEndDate, cadence);
        requireAtLeastOneRound(dates, planEndDate);

        List<BigDecimal> amounts =
                equalSplitAllocator.allocate(remaining, dates.size(), rateContext.minorUnits());

        // 수수료는 회차마다 붙는다 — 총비용에는 회차 수만큼 반영한다 (명세 §9.3).
        CostRange totalCost = exchangeCostCalculator.costRange(
                remaining, rateContext.toRateRange(), rateContext.spreadRatio(),
                rateContext.feeKrw() * dates.size());

        Long availableBudget = resolveAvailableBudget(input, today, planEndDate);
        BudgetState budgetState = budgetFeasibilityEvaluator.evaluate(availableBudget, totalCost);
        if (budgetState == BudgetState.CONSTRAINT_ADJUSTMENT_REQUIRED) {
            // §21-8 — 예산을 초과하는 계획이라도 초과 사실을 숨기지 않는다.
            warnings.add(WARNING_BUDGET_SHORTFALL);
        }

        List<PlanDraft.Step> steps = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            BigDecimal amount = amounts.get(i);
            steps.add(new PlanDraft.Step(
                    i + 1,
                    dates.get(i),
                    amount,
                    null,
                    toDraftCost(exchangeCostCalculator.costRange(
                            amount, rateContext.toRateRange(), rateContext.spreadRatio(),
                            rateContext.feeKrw())),
                    null,
                    BigDecimal.ZERO,
                    null,
                    null,
                    PlanStepStatus.SCHEDULED,
                    i == 0));
        }

        return new PlanDraft(
                Instant.now(clock), PlannerPolicy.POLICY_VERSION, rateContext,
                deadlineGoalSummary(input, target, held, remaining),
                new PlanDraft.Summary(
                        PlanStatus.DRAFT, planEndDate, steps.size(), 0, steps.size(), 0, 1,
                        toDraftCost(totalCost), budgetState.name(), null),
                steps, warnings);
    }

    private PlanDraft.GoalSummary deadlineGoalSummary(
            PlanInput input, BigDecimal target, BigDecimal held, BigDecimal remaining) {
        return new PlanDraft.GoalSummary(
                input.goalType(), input.purpose(), input.currencyCode(),
                target, null, held, remaining, input.targetDate(), null);
    }

    /**
     * 목표일까지 쓸 수 있는 예산 (명세 §9.6 {@code availableBudget}).
     *
     * <p>입력 예산은 <b>주기당</b> 금액이므로(명세 §5.2), 계획 종료일까지 그 주기가 몇 번 오는지를
     * 세어 곱한다. 예산을 입력하지 않았으면 {@code null} 이며 가능 여부를 판정하지 않는다
     * ({@code BUDGET_NOT_PROVIDED}).
     */
    private Long resolveAvailableBudget(PlanInput input, LocalDate today, LocalDate planEndDate) {
        if (input.budgetAmountKrw() == null) {
            return null;
        }
        Cadence budgetCadence = Cadence.from(input.budgetPeriod());
        int periods = roundScheduleGenerator.generate(today, planEndDate, budgetCadence).size();
        return input.budgetAmountKrw() * periods;
    }

    // ── 정기형 (명세 §10) ──────────────────────────────────────────────────

    private PlanDraft calculateRecurring(PlanInput input, PlanRateContext rateContext, LocalDate today) {
        List<String> warnings = baseWarnings(rateContext);

        Cadence cadence = Cadence.from(input.cadence());
        List<LocalDate> dates = roundScheduleGenerator.generateForHorizon(
                input.startDate(), input.reviewHorizonMonths(), cadence);
        LocalDate reviewEndDate = input.startDate().plusMonths(input.reviewHorizonMonths());
        requireAtLeastOneRound(dates, reviewEndDate);

        long budgetKrw = input.budgetAmountKrw();
        long netBudget = recurringAcquisitionCalculator.netBudget(budgetKrw, rateContext.feeKrw());
        AcquisitionRange perRound = recurringAcquisitionCalculator.acquirableRange(
                netBudget, rateContext.toRateRange(), rateContext.spreadRatio(), rateContext.minorUnits());
        PlanDraft.AcquisitionRange draftPerRound = toDraftAcquisition(perRound);

        // 정기형은 회차 예산이 곧 비용이다 — 확보 외화가 아니라 지출이 고정된다 (명세 §10.3).
        long totalBudget = budgetKrw * dates.size();
        PlanDraft.CostRange totalCost = new PlanDraft.CostRange(totalBudget, totalBudget, totalBudget);
        PlanDraft.CostRange roundCost = new PlanDraft.CostRange(budgetKrw, budgetKrw, budgetKrw);

        List<PlanDraft.Step> steps = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            steps.add(new PlanDraft.Step(
                    i + 1, dates.get(i), perRound.base(), budgetKrw, roundCost, draftPerRound,
                    BigDecimal.ZERO, null, null, PlanStepStatus.SCHEDULED, i == 0));
        }

        BigDecimal held = equalSplitAllocator.normalize(
                BigDecimal.valueOf(input.allocatedHoldingAmount()), rateContext.minorUnits());

        return new PlanDraft(
                Instant.now(clock), PlannerPolicy.POLICY_VERSION, rateContext,
                new PlanDraft.GoalSummary(
                        input.goalType(), input.purpose(), input.currencyCode(),
                        null, budgetKrw, held, BigDecimal.ZERO, reviewEndDate, null),
                new PlanDraft.Summary(
                        // 정기형은 예산이 곧 입력이므로 예산 가능 여부를 판정하지 않는다 (명세 §9.6 은
                        // 마감형 규칙이다). 대신 점검 시점의 누적 확보 외화 범위를 낸다 (§10.3).
                        PlanStatus.DRAFT, reviewEndDate, steps.size(), 0, steps.size(), 0, 1,
                        totalCost, null, toDraftAcquisition(perRound.accumulate(steps.size()))),
                steps, warnings);
    }

    // ── 공통 ──────────────────────────────────────────────────────────────

    /** engine 결과를 도메인 값으로 옮긴다 — api 가 engine 타입에 닿지 않게 하는 경계다. */
    private PlanDraft.CostRange toDraftCost(CostRange range) {
        return new PlanDraft.CostRange(range.lowKrw(), range.baseKrw(), range.highKrw());
    }

    private PlanDraft.AcquisitionRange toDraftAcquisition(AcquisitionRange range) {
        return new PlanDraft.AcquisitionRange(range.low(), range.base(), range.high());
    }

    private List<String> baseWarnings(PlanRateContext rateContext) {
        List<String> warnings = new ArrayList<>();
        if (!rateContext.forecastAvailable()) {
            warnings.add(PlanRateContext.WARNING_FORECAST_UNAVAILABLE);
        }
        return warnings;
    }

    private Cadence resolveCadence(String code, Cadence fallback) {
        return code == null || code.isBlank() ? fallback : Cadence.from(code);
    }

    /** 명세 §8 — 영업일 버퍼를 적용해 실제 회차를 하나 이상 만들 수 있는지 확인한다. */
    private void requireAtLeastOneRound(List<LocalDate> dates, LocalDate endDate) {
        if (dates.isEmpty()) {
            throw new InvalidRequestException(
                    "마감 버퍼를 적용하면 " + endDate + " 까지 회차를 만들 수 없습니다. "
                            + "목표일이나 주기를 조정해 주세요.",
                    "target_date");
        }
    }

    /** 목표를 이미 채웠다 — 신규 회차를 만들지 않는다 (명세 §9.2). */
    public static final String WARNING_TARGET_ALREADY_MET = "TARGET_ALREADY_MET";

    /** 예산이 비용 하단에도 못 미친다 — 금액·날짜·예산 중 하나를 조정해야 한다 (명세 §9.6). */
    public static final String WARNING_BUDGET_SHORTFALL = "BUDGET_SHORTFALL";
}
