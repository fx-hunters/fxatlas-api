package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AcquisitionRange} 테스트 — 확보 외화 범위와 누적 (명세 §10.2·§10.3).
 */
@DisplayName("AcquisitionRange")
class AcquisitionRangeTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("low 는 환율 상단, high 는 환율 하단에서 나온다 — 비용과 방향이 반대다")
    void lowComesFromHighRate() {
        // 같은 예산이면 환율이 높을수록 확보 외화는 줄어든다.
        AcquisitionRange range = new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15"));

        assertThat(range.low()).isLessThan(range.base());
        assertThat(range.base()).isLessThan(range.high());
    }

    @Test
    @DisplayName("null 값은 각각 거부한다")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AcquisitionRange(null, bd("1"), bd("2")))
                .withMessage("low");
        assertThatNullPointerException()
                .isThrownBy(() -> new AcquisitionRange(bd("1"), null, bd("2")))
                .withMessage("base");
        assertThatNullPointerException()
                .isThrownBy(() -> new AcquisitionRange(bd("1"), bd("2"), null))
                .withMessage("high");
    }

    @Test
    @DisplayName("accumulate 는 회차 수만큼 곱한다 (§10.3)")
    void accumulateMultipliesByRoundCount() {
        AcquisitionRange perRound = new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15"));

        AcquisitionRange total = perRound.accumulate(6);

        assertThat(total.low()).isEqualByComparingTo("429.00");
        assertThat(total.base()).isEqualByComparingTo("441.30");
        assertThat(total.high()).isEqualByComparingTo("450.90");
    }

    @Test
    @DisplayName("회차 수 1 이면 그대로다")
    void accumulateOneRoundIsIdentity() {
        AcquisitionRange perRound = new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15"));

        AcquisitionRange total = perRound.accumulate(1);

        assertThat(total.low()).isEqualByComparingTo(perRound.low());
        assertThat(total.base()).isEqualByComparingTo(perRound.base());
        assertThat(total.high()).isEqualByComparingTo(perRound.high());
    }

    @Test
    @DisplayName("회차 수 0 이면 전부 0 이다 — 아직 아무것도 확보하지 못한 상태")
    void accumulateZeroRoundsIsZero() {
        AcquisitionRange total =
                new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15")).accumulate(0);

        assertThat(total.low()).isEqualByComparingTo("0");
        assertThat(total.base()).isEqualByComparingTo("0");
        assertThat(total.high()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("음수 회차 수는 거부한다")
    void accumulateRejectsNegativeRoundCount() {
        AcquisitionRange perRound = new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> perRound.accumulate(-1))
                .withMessageContaining("회차 수는 0 이상");
    }

    @Test
    @DisplayName("누적해도 범위의 순서는 유지된다")
    void accumulatePreservesOrdering() {
        AcquisitionRange total =
                new AcquisitionRange(bd("71.50"), bd("73.55"), bd("75.15")).accumulate(12);

        assertThat(total.low()).isLessThan(total.base());
        assertThat(total.base()).isLessThan(total.high());
    }
}
