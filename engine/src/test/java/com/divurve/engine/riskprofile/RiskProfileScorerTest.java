package com.divurve.engine.riskprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link RiskProfileScorer} 단위 테스트 — Q1~Q3 합계 원점수와 4등급 분류(안정·균형·적극·도전) 및 입력 검증.
 */
class RiskProfileScorerTest {

    private final RiskProfileScorer scorer = new RiskProfileScorer();

    @Test
    void 응답값을_합해_원점수를_낸다() {
        assertThat(scorer.assess(List.of(1, 2, 3)).score()).isEqualTo(6);
        assertThat(scorer.assess(List.of(0, 0, 0)).score()).isZero();
        assertThat(scorer.assess(List.of(3, 3, 3)).score()).isEqualTo(9);
    }

    @Test
    void 합계_0에서_2는_stable() {
        assertThat(scorer.assess(List.of(0, 0, 0)).riskType()).isEqualTo(RiskProfileScorer.STABLE);
        assertThat(scorer.assess(List.of(1, 1, 0)).riskType()).isEqualTo(RiskProfileScorer.STABLE); // 경계 2
    }

    @Test
    void 합계_3에서_4는_balanced() {
        assertThat(scorer.assess(List.of(1, 1, 1)).riskType()).isEqualTo(RiskProfileScorer.BALANCED); // 경계 3
        assertThat(scorer.assess(List.of(2, 1, 1)).riskType()).isEqualTo(RiskProfileScorer.BALANCED); // 경계 4
    }

    @Test
    void 합계_5에서_6은_aggressive() {
        assertThat(scorer.assess(List.of(2, 2, 1)).riskType()).isEqualTo(RiskProfileScorer.AGGRESSIVE); // 경계 5
        assertThat(scorer.assess(List.of(2, 2, 2)).riskType()).isEqualTo(RiskProfileScorer.AGGRESSIVE); // 경계 6
    }

    @Test
    void 합계_7에서_9는_challenging() {
        assertThat(scorer.assess(List.of(3, 2, 2)).riskType()).isEqualTo(RiskProfileScorer.CHALLENGING); // 경계 7
        assertThat(scorer.assess(List.of(3, 3, 3)).riskType()).isEqualTo(RiskProfileScorer.CHALLENGING); // 최대 9
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
    void 문항이_3개를_초과하면_예외() {
        assertThatThrownBy(() -> scorer.assess(List.of(1, 1, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대");
    }

    @Test
    void 선택값이_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> scorer.assess(List.of(4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scorer.assess(List.of(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 선택값이_null_이면_예외() {
        assertThatThrownBy(() -> scorer.assess(Arrays.asList((Integer) null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
