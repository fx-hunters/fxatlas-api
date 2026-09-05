package com.divurve.engine.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link EffectiveSpreadCalculator} 단위 테스트 — base × (1 − discount) 계산과 입력 범위 검증.
 */
class EffectiveSpreadCalculatorTest {

    private final EffectiveSpreadCalculator calculator = new EffectiveSpreadCalculator();

    @Test
    void 우대율을_적용해_실효_스프레드를_계산한다() {
        // 기본 1.75%에 우대율 80% → 0.35%
        assertThat(calculator.effectiveSpreadRatio(0.0175, 0.8)).isEqualTo(0.0035, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 우대율_0이면_기본_스프레드_그대로다() {
        assertThat(calculator.effectiveSpreadRatio(0.0175, 0.0)).isEqualTo(0.0175, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 우대율_1이면_실효_스프레드는_0이다() {
        assertThat(calculator.effectiveSpreadRatio(0.0175, 1.0)).isEqualTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 기본_스프레드가_음수면_예외() {
        assertThatThrownBy(() -> calculator.effectiveSpreadRatio(-0.01, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 우대율이_0미만이면_예외() {
        assertThatThrownBy(() -> calculator.effectiveSpreadRatio(0.0175, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 우대율이_1초과면_예외() {
        assertThatThrownBy(() -> calculator.effectiveSpreadRatio(0.0175, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
