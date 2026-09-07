package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link EqualSplitAllocator} — 균등 분배 (플래너 명세 §9.5, 불변조건 §21-2·3).
 */
@DisplayName("EqualSplitAllocator")
class EqualSplitAllocatorTest {

    private EqualSplitAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new EqualSplitAllocator();
    }

    @Test
    @DisplayName("나누어떨어지면 모든 회차가 같다")
    void allocate_EvenlyDivisible_AllRoundsEqual() {
        List<BigDecimal> amounts = allocator.allocate(new BigDecimal("4000.00"), 4, 2);

        assertThat(amounts).containsExactly(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("반올림 잔여분은 마지막 회차에만 붙는다 — 불변조건 §21-3")
    void allocate_Remainder_GoesToLastRoundOnly() {
        // 1000.00 / 3 = 333.333... → 앞 두 회차 333.33, 마지막이 잔여 0.01 을 흡수
        List<BigDecimal> amounts = allocator.allocate(new BigDecimal("1000.00"), 3, 2);

        assertThat(amounts).containsExactly(
                new BigDecimal("333.33"),
                new BigDecimal("333.33"),
                new BigDecimal("333.34"));
    }

    @Test
    @DisplayName("회차 금액의 합은 총액과 정확히 같다 — 불변조건 §21-2")
    void allocate_Sum_EqualsTotal() {
        BigDecimal total = new BigDecimal("4000.00");

        List<BigDecimal> amounts = allocator.allocate(total, 7, 2);

        assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
    }

    @ParameterizedTest
    @CsvSource({
            "5000.00, 3, 2",
            "5000.00, 7, 2",
            "1234567, 9, 0",
            "0.07, 4, 2",
    })
    @DisplayName("어떤 조합에서도 합계가 총액과 같다")
    void allocate_SumInvariant_HoldsAcrossInputs(String total, int roundCount, int minorUnits) {
        BigDecimal expected = allocator.normalize(new BigDecimal(total), minorUnits);

        List<BigDecimal> amounts = allocator.allocate(new BigDecimal(total), roundCount, minorUnits);

        assertThat(amounts).hasSize(roundCount);
        assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("JPY 처럼 소수 자릿수가 0이면 정수 단위로 나눈다")
    void allocate_ZeroMinorUnits_UsesWholeUnits() {
        List<BigDecimal> amounts = allocator.allocate(new BigDecimal("100000"), 3, 0);

        assertThat(amounts).containsExactly(
                new BigDecimal("33333"),
                new BigDecimal("33333"),
                new BigDecimal("33334"));
    }

    @Test
    @DisplayName("회차가 1이면 총액이 그대로 한 회차다")
    void allocate_SingleRound_ReturnsTotal() {
        assertThat(allocator.allocate(new BigDecimal("777.77"), 1, 2))
                .containsExactly(new BigDecimal("777.77"));
    }

    @Test
    @DisplayName("총액이 0이면 모든 회차가 0이다")
    void allocate_ZeroTotal_AllRoundsZero() {
        assertThat(allocator.allocate(BigDecimal.ZERO, 3, 2))
                .containsExactly(
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("총액보다 세밀한 입력은 통화 최소 단위로 먼저 반올림된다")
    void allocate_SubUnitTotal_IsNormalizedFirst() {
        List<BigDecimal> amounts = allocator.allocate(new BigDecimal("100.005"), 2, 2);

        assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(new BigDecimal("100.01"));
    }

    @Test
    @DisplayName("회차 수가 1 미만이면 거부한다")
    void allocate_RoundCountBelowOne_Throws() {
        assertThatThrownBy(() -> allocator.allocate(BigDecimal.TEN, 0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("음수 금액은 거부한다")
    void normalize_NegativeAmount_Throws() {
        assertThatThrownBy(() -> allocator.normalize(new BigDecimal("-1"), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("음수 소수 자릿수는 거부한다")
    void normalize_NegativeMinorUnits_Throws() {
        assertThatThrownBy(() -> allocator.normalize(BigDecimal.TEN, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("금액이 null 이면 거부한다")
    void normalize_Null_Throws() {
        assertThatThrownBy(() -> allocator.normalize(null, 2))
                .isInstanceOf(NullPointerException.class);
    }
}
