package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ExchangeCostCalculator} 테스트 — 외화 준비 비용 (명세 §9.3).
 *
 * <p>기대값은 전부 손으로 계산해 적었다. 계산식이 바뀌면 이 숫자들이 먼저 깨지므로
 * 커밋 본문에 변경 전/후 수치를 남기게 된다(CLAUDE.md 7장).
 */
@DisplayName("ExchangeCostCalculator")
class ExchangeCostCalculatorTest {

    private static final BigDecimal USD_RATE = new BigDecimal("1359.50");
    private static final double SPREAD = 0.0035;

    private final ExchangeCostCalculator calculator = new ExchangeCostCalculator();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("effectiveRate")
    class EffectiveRate {

        @Test
        @DisplayName("환율에 스프레드를 곱한다")
        void appliesSpread() {
            // 1359.50 × 1.0035 = 1364.25825
            assertThat(calculator.effectiveRate(USD_RATE, SPREAD))
                    .isEqualByComparingTo("1364.25825");
        }

        @Test
        @DisplayName("스프레드가 0 이면 환율 그대로다")
        void zeroSpreadIsIdentity() {
            assertThat(calculator.effectiveRate(USD_RATE, 0.0)).isEqualByComparingTo(USD_RATE);
        }

        @Test
        @DisplayName("환율이 0 이면 거부한다")
        void rejectsZeroRate() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.effectiveRate(BigDecimal.ZERO, SPREAD))
                    .withMessageContaining("환율은 0보다 커야 합니다");
        }

        @Test
        @DisplayName("환율이 음수면 거부한다")
        void rejectsNegativeRate() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.effectiveRate(bd("-1"), SPREAD))
                    .withMessageContaining("환율은 0보다 커야 합니다");
        }

        @Test
        @DisplayName("음수 스프레드는 거부한다 — 시장보다 유리한 환율을 만들지 않는다")
        void rejectsNegativeSpread() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.effectiveRate(USD_RATE, -0.001))
                    .withMessageContaining("스프레드 비율은 0 이상");
        }

        @Test
        @DisplayName("null 환율은 거부한다")
        void rejectsNullRate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calculator.effectiveRate(null, SPREAD))
                    .withMessage("perUnitRate");
        }
    }

    @Nested
    @DisplayName("cost")
    class Cost {

        @Test
        @DisplayName("cost = amount × rate × (1 + spread) + fee (§9.3)")
        void appliesFormula() {
            // 1000 × 1359.50 × 1.0035 = 1,364,258.25 → 반올림 1,364,258 + 3000 = 1,367,258
            assertThat(calculator.cost(bd("1000"), USD_RATE, SPREAD, 3000L)).isEqualTo(1_367_258L);
        }

        @Test
        @DisplayName("원 단위로 반올림한다 (HALF_UP)")
        void roundsToWon() {
            // 1 × 1000.5 × 1.0 = 1000.5 → 1001
            assertThat(calculator.cost(BigDecimal.ONE, bd("1000.5"), 0.0, 0L)).isEqualTo(1001L);
            // 1 × 1000.4 × 1.0 = 1000.4 → 1000
            assertThat(calculator.cost(BigDecimal.ONE, bd("1000.4"), 0.0, 0L)).isEqualTo(1000L);
        }

        @Test
        @DisplayName("금액이 0 이면 수수료만 남는다")
        void zeroAmountLeavesOnlyFee() {
            assertThat(calculator.cost(BigDecimal.ZERO, USD_RATE, SPREAD, 3000L)).isEqualTo(3000L);
        }

        @Test
        @DisplayName("수수료가 0 이면 환전 비용만 나온다")
        void zeroFee() {
            assertThat(calculator.cost(bd("1000"), USD_RATE, SPREAD, 0L)).isEqualTo(1_364_258L);
        }

        @Test
        @DisplayName("음수 금액은 거부한다")
        void rejectsNegativeAmount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.cost(bd("-1"), USD_RATE, SPREAD, 0L))
                    .withMessageContaining("외화 금액은 0 이상");
        }

        @Test
        @DisplayName("음수 수수료는 거부한다")
        void rejectsNegativeFee() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.cost(bd("1000"), USD_RATE, SPREAD, -1L))
                    .withMessageContaining("수수료는 0 이상");
        }

        @Test
        @DisplayName("null 금액은 거부한다")
        void rejectsNullAmount() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calculator.cost(null, USD_RATE, SPREAD, 0L))
                    .withMessage("amount");
        }
    }

    @Nested
    @DisplayName("costRange")
    class CostRangeTest {

        @Test
        @DisplayName("환율 하단·기준·상단의 비용 3종을 만든다 (§9.3)")
        void producesThreeCosts() {
            RateRange rates = new RateRange(bd("1330.60"), USD_RATE, bd("1389.02"));

            CostRange range = calculator.costRange(bd("1000"), rates, SPREAD, 3000L);

            // 1000 × 1330.60 × 1.0035 = 1,335,257.10 → 1,335,257 + 3000
            assertThat(range.lowKrw()).isEqualTo(1_338_257L);
            assertThat(range.baseKrw()).isEqualTo(1_367_258L);
            // 1000 × 1389.02 × 1.0035 = 1,393,881.57 → 1,393,882 + 3000
            assertThat(range.highKrw()).isEqualTo(1_396_882L);
        }

        @Test
        @DisplayName("비용은 환율에 비례한다 — low <= base <= high")
        void costIsProportionalToRate() {
            RateRange rates = new RateRange(bd("1330.60"), USD_RATE, bd("1389.02"));

            CostRange range = calculator.costRange(bd("1000"), rates, SPREAD, 3000L);

            assertThat(range.lowKrw()).isLessThan(range.baseKrw());
            assertThat(range.baseKrw()).isLessThan(range.highKrw());
        }

        @Test
        @DisplayName("환율 범위가 한 점이면 세 비용이 같다")
        void degenerateRateRangeGivesEqualCosts() {
            RateRange rates = new RateRange(USD_RATE, USD_RATE, USD_RATE);

            CostRange range = calculator.costRange(bd("1000"), rates, SPREAD, 3000L);

            assertThat(range.lowKrw()).isEqualTo(range.baseKrw()).isEqualTo(range.highKrw());
        }

        @Test
        @DisplayName("null 환율 범위는 거부한다")
        void rejectsNullRates() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calculator.costRange(bd("1000"), null, SPREAD, 0L))
                    .withMessage("rates");
        }
    }
}
