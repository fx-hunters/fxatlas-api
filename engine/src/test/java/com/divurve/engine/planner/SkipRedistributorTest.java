package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SkipRedistributor} — 건너뛰기 후 재분배 (플래너 명세 §15).
 */
@DisplayName("SkipRedistributor")
class SkipRedistributorTest {

    private SkipRedistributor redistributor;

    @BeforeEach
    void setUp() {
        redistributor = new SkipRedistributor(new EqualSplitAllocator());
    }

    @Test
    @DisplayName("남은 금액을 남은 회차로 균등 재분배한다")
    void redistribute_SplitsRemainingEvenly() {
        SkipRedistribution result = redistributor.redistribute(
                new BigDecimal("5000.00"), new BigDecimal("2000.00"), 3, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("3000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.roundAmounts()).hasSize(3);
    }

    @Test
    @DisplayName("재분배 후에도 회차 합은 남은 금액과 같다 — 불변조건 §21-2")
    void redistribute_Sum_EqualsRemaining() {
        SkipRedistribution result = redistributor.redistribute(
                new BigDecimal("5000.00"), new BigDecimal("1234.56"), 7, 2);

        assertThat(result.roundAmounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.newRemainingAmount());
    }

    @Test
    @DisplayName("이미 목표를 넘겼으면 남은 금액은 0이다")
    void redistribute_HeldExceedsTarget_ReturnsZeroRemaining() {
        SkipRedistribution result = redistributor.redistribute(
                new BigDecimal("1000.00"), new BigDecimal("1500.00"), 3, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("0");
        assertThat(result.roundAmounts()).allSatisfy(
                amount -> assertThat(amount).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("남은 회차가 없으면 재분배하지 않고 남은 금액을 그대로 드러낸다 — 불변조건 §21-8")
    void redistribute_NoRemainingRounds_ExposesShortfall() {
        SkipRedistribution result = redistributor.redistribute(
                new BigDecimal("5000.00"), new BigDecimal("2000.00"), 0, 2);

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("3000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("0");
        assertThat(result.roundAmounts()).isEmpty();
    }

    @Test
    @DisplayName("회차 목록은 방어적으로 복사되어 외부에서 바꿀 수 없다")
    void redistribution_RoundAmounts_AreImmutable() {
        SkipRedistribution result = redistributor.redistribute(
                new BigDecimal("100.00"), BigDecimal.ZERO, 2, 2);

        assertThatThrownBy(() -> result.roundAmounts().add(BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("음수 남은 회차는 거부한다")
    void redistribute_NegativeRemainingRounds_Throws() {
        assertThatThrownBy(() -> redistributor.redistribute(
                BigDecimal.TEN, BigDecimal.ZERO, -1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("null 인자는 거부한다")
    void redistribute_NullArguments_Throw() {
        assertThatThrownBy(() -> redistributor.redistribute(null, BigDecimal.ZERO, 1, 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> redistributor.redistribute(BigDecimal.TEN, null, 1, 2))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("분배기가 null 이면 생성을 거부한다")
    void constructor_NullDependency_Throws() {
        assertThatThrownBy(() -> new SkipRedistributor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("재분배 결과의 null 필드는 거부한다")
    void redistribution_NullFields_Throw() {
        assertThatThrownBy(() -> new SkipRedistribution(null, BigDecimal.ZERO, java.util.List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkipRedistribution(BigDecimal.ZERO, null, java.util.List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkipRedistribution(BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
