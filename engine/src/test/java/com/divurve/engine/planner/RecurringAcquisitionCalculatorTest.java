package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RecurringAcquisitionCalculator} 테스트 — 정기형 확보 외화 (명세 §10.2·§10.3).
 */
@DisplayName("RecurringAcquisitionCalculator")
class RecurringAcquisitionCalculatorTest {

    private static final double SPREAD = 0.0035;

    private final ExchangeCostCalculator exchangeCostCalculator = new ExchangeCostCalculator();
    private final RecurringAcquisitionCalculator calculator =
            new RecurringAcquisitionCalculator(exchangeCostCalculator);

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("null 의존성은 거부한다")
    void rejectsNullDependency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RecurringAcquisitionCalculator(null))
                .withMessage("exchangeCostCalculator");
    }

    @Nested
    @DisplayName("netBudget")
    class NetBudget {

        @Test
        @DisplayName("예산에서 수수료를 뺀다")
        void subtractsFee() {
            assertThat(calculator.netBudget(1_000_000L, 3000L)).isEqualTo(997_000L);
        }

        @Test
        @DisplayName("수수료가 0 이면 예산 그대로다")
        void zeroFee() {
            assertThat(calculator.netBudget(1_000_000L, 0L)).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("수수료가 예산보다 크면 0 이다 — 음수 예산을 만들지 않는다")
        void feeExceedingBudgetGivesZero() {
            assertThat(calculator.netBudget(1000L, 3000L)).isZero();
        }

        @Test
        @DisplayName("수수료가 예산과 같으면 0 이다")
        void feeEqualToBudgetGivesZero() {
            assertThat(calculator.netBudget(3000L, 3000L)).isZero();
        }

        @Test
        @DisplayName("음수 예산은 거부한다")
        void rejectsNegativeBudget() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.netBudget(-1L, 0L))
                    .withMessageContaining("회차 예산은 0 이상");
        }

        @Test
        @DisplayName("음수 수수료는 거부한다")
        void rejectsNegativeFee() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.netBudget(1000L, -1L))
                    .withMessageContaining("수수료는 0 이상");
        }
    }

    @Nested
    @DisplayName("acquirableRange")
    class AcquirableRange {

        private final RateRange rates = new RateRange(bd("1330.60"), bd("1359.50"), bd("1389.02"));

        @Test
        @DisplayName("환율이 높을수록 확보 외화가 적다 — 비용과 방향이 반대다 (§10.2)")
        void higherRateGivesLessForeignCurrency() {
            AcquisitionRange range = calculator.acquirableRange(997_000L, rates, SPREAD, 2);

            assertThat(range.low()).isLessThan(range.base());
            assertThat(range.base()).isLessThan(range.high());
        }

        @Test
        @DisplayName("low 는 환율 상단, high 는 환율 하단에서 나온다")
        void boundsComeFromOppositeRates() {
            AcquisitionRange range = calculator.acquirableRange(997_000L, rates, SPREAD, 2);

            // low  = 997000 / (1389.02 × 1.0035) = 715.2688... → 내림 715.26
            assertThat(range.low()).isEqualByComparingTo("715.26");
            // base = 997000 / (1359.50 × 1.0035) = 730.8016... → 내림 730.80
            assertThat(range.base()).isEqualByComparingTo("730.80");
            // high = 997000 / (1330.60 × 1.0035) = 746.6737... → 내림 746.67
            assertThat(range.high()).isEqualByComparingTo("746.67");
        }

        @Test
        @DisplayName("확보 외화는 통화 최소 단위로 내림한다 — 확보되지 않을 금액을 표시하지 않는다")
        void roundsDown() {
            AcquisitionRange range = calculator.acquirableRange(997_000L, rates, SPREAD, 2);

            assertThat(range.base().scale()).isEqualTo(2);
            // 내림이므로 실제 몫보다 크지 않다.
            BigDecimal exact = BigDecimal.valueOf(997_000L)
                    .divide(exchangeCostCalculator.effectiveRate(rates.base(), SPREAD), new java.math.MathContext(20));
            assertThat(range.base()).isLessThanOrEqualTo(exact);
        }

        @Test
        @DisplayName("JPY 처럼 소수 자릿수가 0 인 통화는 정수로 내림한다")
        void zeroMinorUnitsCurrency() {
            RateRange yenRates = new RateRange(bd("8.90"), bd("9.00"), bd("9.10"));

            AcquisitionRange range = calculator.acquirableRange(1_000_000L, yenRates, SPREAD, 0);

            assertThat(range.base().scale()).isZero();
            // 1000000 / (9.00 × 1.0035) = 110,723.7... → 내림 110,723
            assertThat(range.base()).isEqualByComparingTo("110723");
        }

        @Test
        @DisplayName("예산이 0 이면 확보 외화도 0 이다")
        void zeroBudgetGivesZero() {
            AcquisitionRange range = calculator.acquirableRange(0L, rates, SPREAD, 2);

            assertThat(range.low()).isEqualByComparingTo("0");
            assertThat(range.base()).isEqualByComparingTo("0");
            assertThat(range.high()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("마감형 비용 계산의 역함수다 — 확보 외화를 되돌리면 예산을 넘지 않는다")
        void isInverseOfCostCalculation() {
            // 두 계산이 같은 실효 환율을 쓰지 않으면 "이 예산으로 이만큼" 과
            // "이만큼 준비에 이 비용" 이 서로 어긋난다.
            long netBudget = 997_000L;
            AcquisitionRange range = calculator.acquirableRange(netBudget, rates, SPREAD, 2);

            long costOfBase = exchangeCostCalculator.cost(range.base(), rates.base(), SPREAD, 0L);

            assertThat(costOfBase).isLessThanOrEqualTo(netBudget);
        }

        @Test
        @DisplayName("음수 예산은 거부한다")
        void rejectsNegativeBudget() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.acquirableRange(-1L, rates, SPREAD, 2))
                    .withMessageContaining("예산은 0 이상");
        }

        @Test
        @DisplayName("음수 소수 자릿수는 거부한다")
        void rejectsNegativeMinorUnits() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.acquirableRange(1000L, rates, SPREAD, -1))
                    .withMessageContaining("통화 소수 자릿수는 0 이상");
        }

        @Test
        @DisplayName("null 환율 범위는 거부한다")
        void rejectsNullRates() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calculator.acquirableRange(1000L, null, SPREAD, 2))
                    .withMessage("rates");
        }
    }

    @Test
    @DisplayName("점검 기간까지의 누적 확보 외화 범위 (§10.3)")
    void accumulatedRangeOverReviewHorizon() {
        RateRange rates = new RateRange(bd("1330.60"), bd("1359.50"), bd("1389.02"));
        long netBudget = calculator.netBudget(1_000_000L, 3000L);

        AcquisitionRange perRound = calculator.acquirableRange(netBudget, rates, SPREAD, 2);
        AcquisitionRange total = perRound.accumulate(6);

        // 조건부 범위다 — 모든 회차가 같은 환율 범위 안에 있다고 가정한다.
        assertThat(total.low()).isEqualByComparingTo("4291.56");
        assertThat(total.base()).isEqualByComparingTo("4384.80");
        assertThat(total.high()).isEqualByComparingTo("4480.02");
    }
}
