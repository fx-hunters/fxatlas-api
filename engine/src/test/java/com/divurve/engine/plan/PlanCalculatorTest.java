package com.divurve.engine.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PlanCalculator 순수 함수 테스트.
 * 모든 메서드가 상태를 변경하지 않고 입력만으로 출력이 결정되는지 검증한다.
 */
@DisplayName("PlanCalculator")
class PlanCalculatorTest {

    private static final double EPSILON = 1e-10;

    @Nested
    @DisplayName("calculateSecuredRatio")
    class CalculateSecuredRatioTest {

        @Test
        @DisplayName("정상 케이스: 50% 달성")
        void normalCase() {
            double result = PlanCalculator.calculateSecuredRatio(500.0, 1000.0);
            assertEquals(0.5, result, EPSILON);
        }

        @Test
        @DisplayName("100% 이상 달성 시 1.0으로 캡핑")
        void cappedAtOne() {
            double result = PlanCalculator.calculateSecuredRatio(1500.0, 1000.0);
            assertEquals(1.0, result, EPSILON);
        }

        @Test
        @DisplayName("실행액이 0일 때 0.0 반환")
        void zeroExecuted() {
            double result = PlanCalculator.calculateSecuredRatio(0.0, 1000.0);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("목표액이 0일 때 0.0 반환")
        void zeroTarget() {
            double result = PlanCalculator.calculateSecuredRatio(500.0, 0.0);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("목표액이 음수에 가까운 값일 때 0.0 반환")
        void negativeTarget() {
            double result = PlanCalculator.calculateSecuredRatio(500.0, 1e-11);
            assertEquals(0.0, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("calculateRemainingAmount")
    class CalculateRemainingAmountTest {

        @Test
        @DisplayName("남은 금액 정상 계산")
        void normalCase() {
            double result = PlanCalculator.calculateRemainingAmount(1000.0, 600.0);
            assertEquals(400.0, result, EPSILON);
        }

        @Test
        @DisplayName("초과 달성 시 0.0 반환")
        void overAchieved() {
            double result = PlanCalculator.calculateRemainingAmount(1000.0, 1500.0);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("미달성 시 전체 금액 반환")
        void notAchieved() {
            double result = PlanCalculator.calculateRemainingAmount(1000.0, 0.0);
            assertEquals(1000.0, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("calculateRemainingSteps")
    class CalculateRemainingStepsTest {

        @Test
        @DisplayName("정상 케이스: 처리한 3회차, 건너뛰기 1회")
        void normalCase() {
            int result = PlanCalculator.calculateRemainingSteps(10, 3, 1);
            assertEquals(6, result); // 10 - 3 - 1 = 6 (남은 회차 - 건너뛴 회차)
        }

        @Test
        @DisplayName("남은 회차가 없을 때 0 반환")
        void noRemainingSteps() {
            int result = PlanCalculator.calculateRemainingSteps(10, 10, 0);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("음수가 나올 경우 0으로 처리")
        void negativeResult() {
            int result = PlanCalculator.calculateRemainingSteps(5, 10, 5);
            assertEquals(0, result);
        }

        @ParameterizedTest
        @CsvSource({
            "10,1,0,9",   // 처리 1, 건너뛰기 0 → 남은 9
            "20,5,2,13",  // 처리 5, 건너뛰기 2 → 남은 13 (20-5-2)
            "100,50,10,40", // 처리 50, 건너뛰기 10 → 남은 40 (100-50-10)
        })
        @DisplayName("여러 조합의 계산 검증")
        void parameterized(int total, int current, int skipped, int expected) {
            int result = PlanCalculator.calculateRemainingSteps(total, current, skipped);
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("calculateBurdenIncreaseRatio")
    class CalculateBurdenIncreaseRatioTest {

        @Test
        @DisplayName("정상 케이스: 부담 50% 증가")
        void normalCase() {
            double result = PlanCalculator.calculateBurdenIncreaseRatio(1000.0, 10, 100.0);
            assertEquals(0.0, result, EPSILON); // 1000/10 = 100, 증가율 = (100-100)/100 = 0
        }

        @Test
        @DisplayName("부담 증가: 1000을 8회에 나누면 125")
        void burdenIncreases() {
            double result = PlanCalculator.calculateBurdenIncreaseRatio(1000.0, 8, 100.0);
            assertEquals(0.25, result, EPSILON); // (125-100)/100 = 0.25
        }

        @Test
        @DisplayName("남은 회차가 0이면 0.0 반환")
        void noRemainingSteps() {
            double result = PlanCalculator.calculateBurdenIncreaseRatio(1000.0, 0, 100.0);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("현재 부담이 0에 가까우면 0.0 반환")
        void zeroCurrentAmount() {
            double result = PlanCalculator.calculateBurdenIncreaseRatio(1000.0, 10, 1e-11);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("남은 금액이 음수이면 음수 비율 반환")
        void negativeRemaining() {
            double result = PlanCalculator.calculateBurdenIncreaseRatio(-100.0, 10, 100.0);
            assertEquals(-1.0, result, EPSILON); // (-100/10 - 100) / 100 = -1.1 ... 음수 처리
        }
    }

    @Nested
    @DisplayName("calculateAchieveProbAfterSkip")
    class CalculateAchieveProbAfterSkipTest {

        @Test
        @DisplayName("정상 케이스: 회차 절반 건너뛰면 확률 절반")
        void normalCase() {
            double result = PlanCalculator.calculateAchieveProbAfterSkip(0.8, 10, 5);
            assertEquals(0.4, result, EPSILON); // 0.8 * (5/10) = 0.4
        }

        @Test
        @DisplayName("회차 모두 건너뛰면 확률 0.0")
        void allSkipped() {
            double result = PlanCalculator.calculateAchieveProbAfterSkip(0.8, 10, 0);
            assertEquals(0.0, result, EPSILON);
        }

        @Test
        @DisplayName("회차 변화 없으면 확률 유지")
        void noChange() {
            double result = PlanCalculator.calculateAchieveProbAfterSkip(0.9, 10, 10);
            assertEquals(0.9, result, EPSILON);
        }

        @Test
        @DisplayName("전체 회차가 0이면 0.0 반환")
        void zeroTotal() {
            double result = PlanCalculator.calculateAchieveProbAfterSkip(0.8, 0, 5);
            assertEquals(0.0, result, EPSILON);
        }

        @ParameterizedTest
        @CsvSource({
            "1.0,10,10,1.0",
            "0.5,10,5,0.25",
            "0.6,20,10,0.3",
        })
        @DisplayName("여러 확률과 회차 조합 검증")
        void parameterized(double prob, int total, int remaining, double expected) {
            double result = PlanCalculator.calculateAchieveProbAfterSkip(prob, total, remaining);
            assertEquals(expected, result, EPSILON);
        }
    }
}
