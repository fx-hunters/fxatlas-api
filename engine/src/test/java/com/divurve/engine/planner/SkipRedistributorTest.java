package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SkipRedistributor} 테스트 — 건너뛰기 후 재분배 (명세 §15·§21-1·§21-2·§21-3).
 *
 * <p><b>§21-1</b> {@code R = max(targetAmount - heldAmount, 0)} — 남은 금액은 음수가 되지 않는다.
 */
@DisplayName("SkipRedistributor")
class SkipRedistributorTest {

    private final EqualSplitAllocator allocator = new EqualSplitAllocator();
    private final SkipRedistributor redistributor = new SkipRedistributor(allocator);

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("null 의존성은 거부한다")
    void rejectsNullDependency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SkipRedistributor(null))
                .withMessage("equalSplitAllocator");
    }

    @Test
    @DisplayName("남은 금액을 남은 회차에 균등 분배한다 (§15)")
    void redistributesRemainingAcrossRemainingRounds() {
        // 목표 6000, 확보 2000 → 남은 4000 을 4회차로
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 4, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.roundAmounts()).hasSize(4);
        assertThat(sum(result.roundAmounts())).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("건너뛰면 남은 회차당 금액이 늘어난다")
    void skippingIncreasesPerRoundAmount() {
        SkipRedistribution before = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 4, 2);
        SkipRedistribution after = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 3, 2);

        assertThat(after.perRoundAmount()).isGreaterThan(before.perRoundAmount());
    }

    @Test
    @DisplayName("합계는 남은 금액과 정확히 같고 잔여분은 마지막 회차에만 (§21-2·3)")
    void sumEqualsRemainingWithRemainderInLastRound() {
        SkipRedistribution result = redistributor.redistribute(bd("1000.00"), BigDecimal.ZERO, 3, 2);

        assertThat(sum(result.roundAmounts())).isEqualByComparingTo("1000.00");
        assertThat(result.roundAmounts().get(0)).isEqualByComparingTo("333.33");
        assertThat(result.roundAmounts().get(1)).isEqualByComparingTo("333.33");
        assertThat(result.roundAmounts().get(2)).isEqualByComparingTo("333.34");
    }

    @Test
    @DisplayName("perRoundAmount 는 첫 회차 금액이다 — 잔여분이 붙은 마지막이 아니다")
    void perRoundAmountIsFirstRound() {
        SkipRedistribution result = redistributor.redistribute(bd("1000.00"), BigDecimal.ZERO, 3, 2);

        assertThat(result.perRoundAmount()).isEqualByComparingTo(result.roundAmounts().get(0));
    }

    @Test
    @DisplayName("이미 목표를 넘겼으면 남은 금액은 0 이다 (§21-1)")
    void heldExceedingTargetGivesZeroRemaining() {
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("7000.00"), 3, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("0");
        assertThat(sum(result.roundAmounts())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("목표와 확보액이 같으면 남은 금액은 0 이다")
    void heldEqualToTargetGivesZeroRemaining() {
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("6000.00"), 3, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("남은 회차가 없으면 회차 목록이 비고 남은 금액은 그대로 드러난다 (§21-8)")
    void noRemainingRoundsExposesShortfall() {
        // 조정이 필요하다는 사실을 숨기지 않는다 — 금액을 0 으로 만들지 않는다.
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 0, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("0");
        assertThat(result.roundAmounts()).isEmpty();
    }

    @Test
    @DisplayName("남은 회차가 없고 남은 금액도 0 이면 전부 0 이다")
    void noRemainingRoundsAndNothingLeft() {
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("6000.00"), 0, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("0");
        assertThat(result.roundAmounts()).isEmpty();
    }

    @Test
    @DisplayName("남은 회차가 하나면 전액이 거기로 간다")
    void singleRemainingRoundTakesEverything() {
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 1, 2);

        assertThat(result.roundAmounts()).hasSize(1);
        assertThat(result.roundAmounts().get(0)).isEqualByComparingTo("4000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("JPY 처럼 소수 자릿수가 0 인 통화도 합계가 맞는다")
    void zeroMinorUnitsCurrency() {
        SkipRedistribution result = redistributor.redistribute(bd("100000"), bd("30000"), 7, 0);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("70000");
        assertThat(sum(result.roundAmounts())).isEqualByComparingTo("70000");
        assertThat(result.roundAmounts()).allSatisfy(amount -> assertThat(amount.scale()).isZero());
    }

    @Test
    @DisplayName("음수 남은 회차 수는 거부한다")
    void rejectsNegativeRemainingRoundCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> redistributor.redistribute(bd("6000"), bd("2000"), -1, 2))
                .withMessageContaining("남은 회차 수는 0 이상");
    }

    @Test
    @DisplayName("null 금액은 각각 거부한다")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> redistributor.redistribute(null, bd("2000"), 3, 2))
                .withMessage("targetAmount");
        assertThatNullPointerException()
                .isThrownBy(() -> redistributor.redistribute(bd("6000"), null, 3, 2))
                .withMessage("currentHeldAmount");
    }

    @Test
    @DisplayName("재분배 결과가 예산을 넘는지는 여기서 판정하지 않는다 (§15)")
    void doesNotEvaluateBudget() {
        // 초과 시 자동 적용하지 말고 조정 선택지를 제시하라는 것이 명세 §15 다.
        // 판정은 BudgetFeasibilityEvaluator, 선택지 제시는 호출부의 몫이다.
        SkipRedistribution result = redistributor.redistribute(bd("6000.00"), bd("2000.00"), 2, 2);

        assertThat(result.perRoundAmount()).isEqualByComparingTo("2000.00");
        assertThat(result).isInstanceOf(SkipRedistribution.class);
    }
}
