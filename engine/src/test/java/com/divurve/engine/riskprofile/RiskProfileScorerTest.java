package com.divurve.engine.riskprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link RiskProfileScorer} 단위 테스트 — 원점수 합계와 평균 기반 등급 삼분류(안정·균형·유연) 및 입력 검증.
 */
class RiskProfileScorerTest {

    private final RiskProfileScorer scorer = new RiskProfileScorer();

    @Test
    void 응답값을_합해_원점수를_낸다() {
        assertThat(scorer.assess(List.of(1, 2, 3)).score()).isEqualTo(6);
    }

    @Test
    void 평균이_안정_상한_이하면_stable() {
        // avg = 5/3 (경계) → stable
        assertThat(scorer.assess(List.of(1, 1, 3)).riskType()).isEqualTo(RiskProfileScorer.STABLE);
        assertThat(scorer.assess(List.of(1)).riskType()).isEqualTo(RiskProfileScorer.STABLE);
    }

    @Test
    void 평균이_균형_구간이면_balanced() {
        // avg = 2.0, 그리고 경계 avg = 7/3 → balanced
        assertThat(scorer.assess(List.of(1, 2, 3)).riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(scorer.assess(List.of(2, 2, 3)).riskType()).isEqualTo(RiskProfileScorer.BALANCED);
    }

    @Test
    void 평균이_균형_상한_초과면_flexible() {
        assertThat(scorer.assess(List.of(3)).riskType()).isEqualTo(RiskProfileScorer.FLEXIBLE);
        assertThat(scorer.assess(List.of(2, 3, 3)).riskType()).isEqualTo(RiskProfileScorer.FLEXIBLE);
    }

    @Test
    void 응답이_null_이면_예외() {
        assertThatThrownBy(() -> scorer.assess(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 응답이_비어_있으면_예외() {
        assertThatThrownBy(() -> scorer.assess(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문항이_5개를_초과하면_예외() {
        assertThatThrownBy(() -> scorer.assess(List.of(1, 1, 1, 1, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대");
    }

    @Test
    void 선택값이_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> scorer.assess(List.of(4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scorer.assess(List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 선택값이_null_이면_예외() {
        assertThatThrownBy(() -> scorer.assess(Arrays.asList((Integer) null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
