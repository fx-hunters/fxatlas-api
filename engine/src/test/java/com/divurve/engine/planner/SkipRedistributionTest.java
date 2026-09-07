package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SkipRedistribution} 테스트 — 재분배 결과의 불변성 (명세 §15·§21-9).
 */
@DisplayName("SkipRedistribution")
class SkipRedistributionTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("세 값을 그대로 담는다")
    void holdsValues() {
        SkipRedistribution result = new SkipRedistribution(
                bd("4000.00"), bd("1000.00"), List.of(bd("1000.00"), bd("1000.00")));

        assertThat(result.newRemainingAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.perRoundAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.roundAmounts()).hasSize(2);
    }

    @Test
    @DisplayName("회차 목록은 방어 복사된다 — 원본을 바꿔도 결과가 흔들리지 않는다")
    void copiesRoundAmounts() {
        List<BigDecimal> source = new ArrayList<>(List.of(bd("1000.00")));

        SkipRedistribution result = new SkipRedistribution(bd("1000.00"), bd("1000.00"), source);
        source.add(bd("9999.00"));

        assertThat(result.roundAmounts()).hasSize(1);
    }

    @Test
    @DisplayName("회차 목록은 수정할 수 없다 — 미리보기는 승인 전까지 변하지 않는다 (§21-9)")
    void roundAmountsAreImmutable() {
        SkipRedistribution result =
                new SkipRedistribution(bd("1000.00"), bd("1000.00"), List.of(bd("1000.00")));

        assertThatThrownBy(() -> result.roundAmounts().add(bd("1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("빈 회차 목록도 유효하다 — 남은 회차가 없는 경우")
    void acceptsEmptyRoundAmounts() {
        SkipRedistribution result =
                new SkipRedistribution(bd("4000.00"), BigDecimal.ZERO, List.of());

        assertThat(result.roundAmounts()).isEmpty();
    }

    @Test
    @DisplayName("null 값은 각각 거부한다")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SkipRedistribution(null, BigDecimal.ZERO, List.of()))
                .withMessage("newRemainingAmount");
        assertThatNullPointerException()
                .isThrownBy(() -> new SkipRedistribution(BigDecimal.ZERO, null, List.of()))
                .withMessage("perRoundAmount");
        assertThatNullPointerException()
                .isThrownBy(() -> new SkipRedistribution(BigDecimal.ZERO, BigDecimal.ZERO, null))
                .withMessage("roundAmounts");
    }
}
