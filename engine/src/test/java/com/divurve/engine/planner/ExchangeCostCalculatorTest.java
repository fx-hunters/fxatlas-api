package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExchangeCostCalculator} — 환율 범위별 예상 원화 비용 (플래너 명세 §9.3).
 */
@DisplayName("ExchangeCostCalculator")
class ExchangeCostCalculatorTest {

    private ExchangeCostCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ExchangeCostCalculator();
    }

    @Test
    @DisplayName("실효 환율은 스프레드를 얹은 값이다")
    void effectiveRate_AppliesSpread() {
        assertThat(calculator.effectiveRate(new BigDecimal("1000"), 0.0175))
                .isEqualByComparingTo("1017.5");
    }

    @Test
    @DisplayName("스프레드가 0이면 환율이 그대로다")
    void effectiveRate_ZeroSpread_ReturnsRate() {
        assertThat(calculator.effectiveRate(new BigDecimal("1350"), 0.0))
                .isEqualByComparingTo("1350");
    }

    @Test
    @DisplayName("cost = amount x rate x (1 + spread) + fee")
    void cost_FollowsSpecFormula() {
        // 1000 x 1300 x 1.0175 = 1,322,750 + 3,000 = 1,325,750
        long cost = calculator.cost(new BigDecimal("1000"), new BigDecimal("1300"), 0.0175, 3000L);

        assertThat(cost).isEqualTo(1_325_750L);
    }

    @Test
    @DisplayName("원 단위로 반올림한다")
    void cost_RoundsToWon() {
        // 1 x 1300.4 x 1.0 = 1300.4 → 1300
        assertThat(calculator.cost(BigDecimal.ONE, new BigDecimal("1300.4"), 0.0, 0L))
                .isEqualTo(1300L);
        // 1 x 1300.5 x 1.0 = 1300.5 → 1301 (HALF_UP)
        assertThat(calculator.cost(BigDecimal.ONE, new BigDecimal("1300.5"), 0.0, 0L))
                .isEqualTo(1301L);
    }

    @Test
    @DisplayName("금액이 0이면 수수료만 남는다")
    void cost_ZeroAmount_ReturnsFeeOnly() {
        assertThat(calculator.cost(BigDecimal.ZERO, new BigDecimal("1350"), 0.0175, 3000L))
                .isEqualTo(3000L);
    }

    @Test
    @DisplayName("비용 범위는 환율 범위에 비례한다")
    void costRange_ScalesWithRates() {
        RateRange rates = new RateRange(
                new BigDecimal("1300"), new BigDecimal("1350"), new BigDecimal("1400"));

        CostRange costs = calculator.costRange(new BigDecimal("1000"), rates, 0.0, 0L);

        assertThat(costs.lowKrw()).isEqualTo(1_300_000L);
        assertThat(costs.baseKrw()).isEqualTo(1_350_000L);
        assertThat(costs.highKrw()).isEqualTo(1_400_000L);
    }

    @Test
    @DisplayName("음수 금액은 거부한다")
    void cost_NegativeAmount_Throws() {
        assertThatThrownBy(() -> calculator.cost(
                new BigDecimal("-1"), new BigDecimal("1350"), 0.0, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("음수 수수료는 거부한다")
    void cost_NegativeFee_Throws() {
        assertThatThrownBy(() -> calculator.cost(
                BigDecimal.ONE, new BigDecimal("1350"), 0.0, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수수료는 0 이상");
    }

    @Test
    @DisplayName("환율이 0 이하면 거부한다")
    void effectiveRate_NonPositiveRate_Throws() {
        assertThatThrownBy(() -> calculator.effectiveRate(BigDecimal.ZERO, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("음수 스프레드는 거부한다")
    void effectiveRate_NegativeSpread_Throws() {
        assertThatThrownBy(() -> calculator.effectiveRate(new BigDecimal("1350"), -0.001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("스프레드 비율은 0 이상");
    }

    @Test
    @DisplayName("null 인자는 거부한다")
    void nullArguments_Throw() {
        RateRange rates = new RateRange(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
        assertThatThrownBy(() -> calculator.effectiveRate(null, 0.0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.cost(null, BigDecimal.TEN, 0.0, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.costRange(BigDecimal.ONE, null, 0.0, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThat(calculator.costRange(BigDecimal.ONE, rates, 0.0, 0L).baseKrw()).isEqualTo(10L);
    }
}
