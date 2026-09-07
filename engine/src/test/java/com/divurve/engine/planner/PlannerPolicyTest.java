package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PlannerPolicy} — 목적별 마감 버퍼 (플래너 명세 §9.4).
 */
@DisplayName("PlannerPolicy")
class PlannerPolicyTest {

    @Test
    @DisplayName("정책 버전은 균등 회차 모델을 가리킨다")
    void policyVersion_IdentifiesEqualSplitModel() {
        assertThat(PlannerPolicy.POLICY_VERSION).isEqualTo("plan-2026.09.1-equal-split");
    }

    @Test
    @DisplayName("학비 목적의 버퍼는 5영업일이다")
    void businessDayBufferFor_Tuition_Returns5() {
        assertThat(PlannerPolicy.businessDayBufferFor("tuition")).isEqualTo(5);
    }

    @Test
    @DisplayName("대문자로 저장된 목적 코드도 같은 버퍼를 준다")
    void businessDayBufferFor_UppercaseTuition_Returns5() {
        assertThat(PlannerPolicy.businessDayBufferFor("TUITION")).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"travel", "investment", "deposit", "custom"})
    @DisplayName("학비 외의 목적은 기본 3영업일이다")
    void businessDayBufferFor_OtherPurposes_Returns3(String purpose) {
        assertThat(PlannerPolicy.businessDayBufferFor(purpose)).isEqualTo(3);
    }

    @Test
    @DisplayName("목적이 null 이면 거부한다")
    void businessDayBufferFor_Null_Throws() {
        assertThatThrownBy(() -> PlannerPolicy.businessDayBufferFor(null))
                .isInstanceOf(NullPointerException.class);
    }
}
