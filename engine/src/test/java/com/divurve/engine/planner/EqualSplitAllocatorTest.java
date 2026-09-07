package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link EqualSplitAllocator} 테스트 — 균등 분배 (명세 §9.5·§21-2·§21-3).
 *
 * <p>여기서 지키는 불변조건 둘은 계획 전체의 신뢰성을 떠받친다.
 * <ul>
 *   <li><b>§21-2</b> 회차 금액의 합은 남은 외화와 정확히 같다</li>
 *   <li><b>§21-3</b> 반올림 잔여분은 마지막 회차에만 반영한다</li>
 * </ul>
 */
@DisplayName("EqualSplitAllocator")
class EqualSplitAllocatorTest {

    private final EqualSplitAllocator allocator = new EqualSplitAllocator();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Nested
    @DisplayName("allocate")
    class Allocate {

        @Test
        @DisplayName("나누어떨어지면 모든 회차가 같다")
        void evenSplit() {
            List<BigDecimal> amounts = allocator.allocate(bd("6000.00"), 6, 2);

            assertThat(amounts).hasSize(6);
            assertThat(amounts).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("1000.00"));
        }

        @Test
        @DisplayName("나누어떨어지지 않으면 잔여분이 마지막 회차에만 붙는다 (§21-3)")
        void remainderGoesToLastRoundOnly() {
            // 1000 / 3 = 333.333... → 앞 회차는 333.33 으로 내림, 마지막이 333.34
            List<BigDecimal> amounts = allocator.allocate(bd("1000.00"), 3, 2);

            assertThat(amounts.get(0)).isEqualByComparingTo("333.33");
            assertThat(amounts.get(1)).isEqualByComparingTo("333.33");
            assertThat(amounts.get(2)).isEqualByComparingTo("333.34");
        }

        @ParameterizedTest(name = "{0} 을 {1}회차로, 소수 {2}자리")
        @CsvSource({
                "1000.00,  3, 2",
                "1000.00,  7, 2",
                "6000.00,  6, 2",
                "0.07,      3, 2",
                "123456,    7, 0",
                "999999,   13, 0",
                "0.001,     3, 3",
        })
        @DisplayName("합계는 언제나 정규화된 총액과 정확히 같다 (§21-2)")
        void sumAlwaysEqualsTotal(BigDecimal total, int roundCount, int minorUnits) {
            BigDecimal normalized = allocator.normalize(total, minorUnits);

            List<BigDecimal> amounts = allocator.allocate(total, roundCount, minorUnits);

            assertThat(sum(amounts)).isEqualByComparingTo(normalized);
        }

        @Test
        @DisplayName("마지막 회차는 앞 회차보다 작지 않다 — 내림했으므로")
        void lastRoundIsNeverSmaller() {
            List<BigDecimal> amounts = allocator.allocate(bd("1000.00"), 7, 2);

            BigDecimal last = amounts.get(amounts.size() - 1);
            assertThat(amounts.subList(0, amounts.size() - 1))
                    .allSatisfy(amount -> assertThat(last).isGreaterThanOrEqualTo(amount));
        }

        @Test
        @DisplayName("회차가 하나면 총액이 그대로 들어간다")
        void singleRoundTakesEverything() {
            List<BigDecimal> amounts = allocator.allocate(bd("1234.56"), 1, 2);

            assertThat(amounts).hasSize(1);
            assertThat(amounts.get(0)).isEqualByComparingTo("1234.56");
        }

        @Test
        @DisplayName("JPY 처럼 소수 자릿수가 0 인 통화도 합계가 맞는다")
        void zeroMinorUnitsCurrency() {
            List<BigDecimal> amounts = allocator.allocate(bd("100000"), 7, 0);

            assertThat(amounts).hasSize(7);
            assertThat(sum(amounts)).isEqualByComparingTo("100000");
            // 100000 / 7 = 14285.71... → 앞 6회차 14285, 마지막 14290
            assertThat(amounts.get(0)).isEqualByComparingTo("14285");
            assertThat(amounts.get(6)).isEqualByComparingTo("14290");
            assertThat(amounts).allSatisfy(amount -> assertThat(amount.scale()).isZero());
        }

        @Test
        @DisplayName("총액이 회차 수보다 작으면 앞 회차가 0 이고 마지막이 전부 가져간다")
        void totalSmallerThanRoundCount() {
            // 0.02 를 5회차로 나누면 앞 4회차는 0.00 이다. 금액을 지어내지 않는다.
            List<BigDecimal> amounts = allocator.allocate(bd("0.02"), 5, 2);

            assertThat(amounts.subList(0, 4))
                    .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.00"));
            assertThat(amounts.get(4)).isEqualByComparingTo("0.02");
            assertThat(sum(amounts)).isEqualByComparingTo("0.02");
        }

        @Test
        @DisplayName("총액이 0 이면 모든 회차가 0 이다")
        void zeroTotal() {
            List<BigDecimal> amounts = allocator.allocate(BigDecimal.ZERO, 4, 2);

            assertThat(amounts).hasSize(4);
            assertThat(sum(amounts)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("총액은 먼저 통화 최소 단위로 반올림된다 — 그 값이 합계 기준이다")
        void totalIsNormalizedFirst() {
            // 1000.005 는 소수 2자리로 1000.01 이 되고, 합계도 그 값과 같아야 한다.
            List<BigDecimal> amounts = allocator.allocate(bd("1000.005"), 3, 2);

            assertThat(sum(amounts)).isEqualByComparingTo("1000.01");
        }

        @Test
        @DisplayName("회차 수가 0 이면 거부한다")
        void rejectsZeroRoundCount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> allocator.allocate(bd("1000"), 0, 2))
                    .withMessageContaining("회차 수는 1 이상");
        }

        @Test
        @DisplayName("회차 수가 음수면 거부한다")
        void rejectsNegativeRoundCount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> allocator.allocate(bd("1000"), -1, 2))
                    .withMessageContaining("회차 수는 1 이상");
        }

        @Test
        @DisplayName("음수 총액은 거부한다")
        void rejectsNegativeTotal() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> allocator.allocate(bd("-1"), 3, 2))
                    .withMessageContaining("금액은 0 이상");
        }
    }

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("통화 최소 단위로 반올림한다 (HALF_UP)")
        void roundsHalfUp() {
            assertThat(allocator.normalize(bd("1000.005"), 2)).isEqualByComparingTo("1000.01");
            assertThat(allocator.normalize(bd("1000.004"), 2)).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("소수 자릿수 0 이면 정수로 반올림한다 — JPY")
        void zeroMinorUnits() {
            assertThat(allocator.normalize(bd("14285.7"), 0)).isEqualByComparingTo("14286");
        }

        @Test
        @DisplayName("이미 자릿수가 맞으면 값이 그대로다")
        void alreadyNormalized() {
            assertThat(allocator.normalize(bd("1000.00"), 2)).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("0 은 그대로 0 이다")
        void zero() {
            assertThat(allocator.normalize(BigDecimal.ZERO, 2)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("음수 금액은 거부한다")
        void rejectsNegativeAmount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> allocator.normalize(bd("-0.01"), 2))
                    .withMessageContaining("금액은 0 이상");
        }

        @Test
        @DisplayName("음수 소수 자릿수는 거부한다")
        void rejectsNegativeMinorUnits() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> allocator.normalize(bd("1000"), -1))
                    .withMessageContaining("통화 소수 자릿수는 0 이상");
        }

        @Test
        @DisplayName("null 금액은 거부한다")
        void rejectsNullAmount() {
            assertThatNullPointerException()
                    .isThrownBy(() -> allocator.normalize(null, 2))
                    .withMessage("amount");
        }
    }
}
