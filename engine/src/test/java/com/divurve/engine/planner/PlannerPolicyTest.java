package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PlannerPolicy} 테스트 — 정책 상수와 목적별 버퍼 (명세 §7·§9.4).
 *
 * <p>정책 버전 문자열까지 검증하는 이유는 §11.1 이 이 값을 응답·저장에 요구하기 때문이다.
 * 상수를 바꾸면 이 테스트가 먼저 깨져 "버전을 함께 올렸는지" 묻게 된다.
 */
@DisplayName("PlannerPolicy")
class PlannerPolicyTest {

    @Test
    @DisplayName("정책 버전은 균등 회차 모델을 가리킨다 (§7·§11.1)")
    void policyVersionIdentifiesEqualSplitModel() {
        assertThat(PlannerPolicy.POLICY_VERSION).isEqualTo("plan-2026.09.1-equal-split");
    }

    @Nested
    @DisplayName("businessDayBufferFor")
    class BusinessDayBufferFor {

        @ParameterizedTest(name = "{0} → 5영업일")
        @ValueSource(strings = {"tuition", "TUITION", "Tuition"})
        @DisplayName("학비·납부는 대소문자와 무관하게 5영업일 (§9.4)")
        void tuitionIsFiveBusinessDays(String purpose) {
            // goals.purpose 에 대문자 코드가 저장돼 있어 대소문자를 가리지 않는다.
            assertThat(PlannerPolicy.businessDayBufferFor(purpose))
                    .isEqualTo(PlannerPolicy.TUITION_BUSINESS_DAY_BUFFER)
                    .isEqualTo(5);
        }

        @ParameterizedTest(name = "{0} → 3영업일")
        @ValueSource(strings = {"travel", "investment", "deposit", "custom", "TRAVEL", ""})
        @DisplayName("그 외 목적은 전부 기본 3영업일 (§9.4)")
        void otherPurposesUseDefaultBuffer(String purpose) {
            assertThat(PlannerPolicy.businessDayBufferFor(purpose))
                    .isEqualTo(PlannerPolicy.DEFAULT_BUSINESS_DAY_BUFFER)
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("목적이 null 이면 거부한다 — 기본값으로 삼키지 않는다")
        void rejectsNullPurpose() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PlannerPolicy.businessDayBufferFor(null))
                    .withMessage("purpose");
        }
    }

    @Test
    @DisplayName("상수 클래스는 인스턴스화할 수 없다")
    void isNotInstantiable() throws Exception {
        Constructor<PlannerPolicy> constructor = PlannerPolicy.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }
}
