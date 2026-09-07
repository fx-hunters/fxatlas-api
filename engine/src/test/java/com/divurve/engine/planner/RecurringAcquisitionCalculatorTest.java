package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RecurringAcquisitionCalculator} — 정기형 확보 외화 범위 (플래너 명세 §10.2).
 */
@DisplayName("RecurringAcquisitionCalculator")
class RecurringAcquisitionCalculatorTest {

    private static final RateRange RATES = new RateRange(
            new BigDecimal("1300"), new BigDecimal("1350"), new BigDecimal("1400"));

    private ExchangeCostCalculator exchangeCostCalculator;
    private RecurringAcquisitionCalculator calculator;

    @BeforeEach
    void setUp() {
        exchangeCostCalculator = new ExchangeCostCalculator();
        calculator = new RecurringAcquisitionCalculator(exchangeCostCalculator);
    }

    @Test
    @DisplayName("실사용 예산은 회차 예산에서 수수료를 뺀 값이다")
    void netBudget_SubtractsFee() {
        assertThat(calculator.netBudget(500_000L, 3000L)).isEqualTo(497_000L);
    }

    @Test
    @DisplayName("수수료가 예산보다 크면 실사용 예산은 0이다")
    void netBudget_FeeExceedsBudget_ReturnsZero() {
        assertThat(calculator.netBudget(1000L, 3000L)).isZero();
    }

    @Test
    @DisplayName("환율이 높을수록 확보 외화가 줄어든다 — 명세 §10.2")
    void acquirableRange_HigherRate_YieldsLessForeign() {
        AcquisitionRange range = calculator.acquirableRange(1_350_000L, RATES, 0.0, 2);

        // low 는 상단 환율(1400), high 는 하단 환율(1300) 에서 나온다
        assertThat(range.low()).isEqualByComparingTo("964.28");
        assertThat(range.base()).isEqualByComparingTo("1000.00");
        assertThat(range.high()).isEqualByComparingTo("1038.46");
        assertThat(range.low()).isLessThan(range.base());
        assertThat(range.base()).isLessThan(range.high());
    }

    @Test
    @DisplayName("스프레드를 반영한 실효 환율로 나눈다 — 마감형 비용의 역함수")
    void acquirableRange_IsInverseOfCost() {
        AcquisitionRange range = calculator.acquirableRange(1_350_000L, RATES, 0.0175, 2);

        // 확보한 외화를 다시 비용으로 환산하면 원래 예산을 넘지 않는다 (내림했으므로)
        long backToKrw = exchangeCostCalculator.cost(range.base(), RATES.base(), 0.0175, 0L);
        assertThat(backToKrw).isLessThanOrEqualTo(1_350_000L);
    }

    @Test
    @DisplayName("확보 외화는 통화 최소 단위로 내림한다 — 확보되지 않을 금액을 표시하지 않는다")
    void acquirableRange_RoundsDown() {
        AcquisitionRange range = calculator.acquirableRange(1000L, RATES, 0.0, 0);

        // 1000 / 1300 = 0.769... → 0 (JPY 처럼 소수 자릿수 0)
        assertThat(range.high()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("예산이 0이면 확보 외화도 0이다")
    void acquirableRange_ZeroBudget_ReturnsZero() {
        AcquisitionRange range = calculator.acquirableRange(0L, RATES, 0.0175, 2);

        assertThat(range.low()).isEqualByComparingTo("0");
        assertThat(range.base()).isEqualByComparingTo("0");
        assertThat(range.high()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("음수 예산은 거부한다")
    void netBudget_NegativeBudget_Throws() {
        assertThatThrownBy(() -> calculator.netBudget(-1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("회차 예산은 0 이상");
    }

    @Test
    @DisplayName("음수 수수료는 거부한다")
    void netBudget_NegativeFee_Throws() {
        assertThatThrownBy(() -> calculator.netBudget(1000L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수수료는 0 이상");
    }

    @Test
    @DisplayName("음수 실사용 예산은 거부한다")
    void acquirableRange_NegativeBudget_Throws() {
        assertThatThrownBy(() -> calculator.acquirableRange(-1L, RATES, 0.0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예산은 0 이상");
    }

    @Test
    @DisplayName("음수 소수 자릿수는 거부한다")
    void acquirableRange_NegativeMinorUnits_Throws() {
        assertThatThrownBy(() -> calculator.acquirableRange(1000L, RATES, 0.0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("통화 소수 자릿수는 0 이상");
    }

    @Test
    @DisplayName("환율 범위가 null 이면 거부한다")
    void acquirableRange_NullRates_Throws() {
        assertThatThrownBy(() -> calculator.acquirableRange(1000L, null, 0.0, 2))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("비용 계산기가 null 이면 생성을 거부한다")
    void constructor_NullDependency_Throws() {
        assertThatThrownBy(() -> new RecurringAcquisitionCalculator(null))
                .isInstanceOf(NullPointerException.class);
    }
}
