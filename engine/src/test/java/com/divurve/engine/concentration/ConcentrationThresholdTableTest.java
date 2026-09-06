package com.divurve.engine.concentration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ConcentrationThresholdTable} — 위험성향 등급 → 집중도 기준선 (명세 §5.3 · §5.5). */
class ConcentrationThresholdTableTest {

    private final ConcentrationThresholdTable table = new ConcentrationThresholdTable();

    @Test
    @DisplayName("명세 §4 fixture — balanced 는 0.60")
    void fixture_balanced는_0_60이다() {
        assertThat(table.thresholdFor("balanced")).isEqualTo(0.60);
    }

    @Test
    @DisplayName("등급이 공격적일수록 기준선이 커진다 (MVP 가설값, 단조 증가만 보장)")
    void 등급별_기준선은_단조_증가한다() {
        assertThat(table.thresholdFor("stable")).isEqualTo(0.50);
        assertThat(table.thresholdFor("balanced")).isEqualTo(0.60);
        assertThat(table.thresholdFor("aggressive")).isEqualTo(0.70);
        assertThat(table.thresholdFor("challenging")).isEqualTo(0.80);
    }

    @Test
    @DisplayName("등급 코드는 대소문자를 가리지 않는다")
    void 등급코드는_대소문자_무관이다() {
        assertThat(table.thresholdFor("BALANCED")).isEqualTo(0.60);
    }

    @Test
    @DisplayName("미측정(null)이면 기준선도 없다 — 임의의 기본 성향을 만들지 않는다 (FR-DG-02)")
    void 미측정이면_null이다() {
        assertThat(table.thresholdFor(null)).isNull();
    }

    @Test
    @DisplayName("모르는 등급이면 null 이다")
    void 모르는_등급이면_null이다() {
        assertThat(table.thresholdFor("unknown_grade")).isNull();
    }
}
