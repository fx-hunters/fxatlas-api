package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link Cadence} 테스트 — 회차 주기 파싱과 날짜 진행 (명세 §5.2·§5.3).
 */
@DisplayName("Cadence")
class CadenceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @Nested
    @DisplayName("from")
    class From {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "weekly, WEEKLY", "WEEKLY, WEEKLY", "Weekly, WEEKLY",
                "biweekly, BIWEEKLY", "BIWEEKLY, BIWEEKLY",
                "monthly, MONTHLY", "MONTHLY, MONTHLY",
        })
        @DisplayName("대소문자를 가리지 않는다 — recur_interval 에 대문자가 저장돼 있다")
        void parsesIgnoringCase(String code, Cadence expected) {
            assertThat(Cadence.from(code)).isEqualTo(expected);
        }

        @Test
        @DisplayName("알 수 없는 코드는 거부한다 — 임의의 주기로 넘어가지 않는다")
        void rejectsUnknownCode() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Cadence.from("daily"))
                    .withMessageContaining("알 수 없는 회차 주기")
                    .withMessageContaining("daily");
        }

        @Test
        @DisplayName("null 코드는 거부한다")
        void rejectsNullCode() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Cadence.from(null))
                    .withMessage("code");
        }
    }

    @Nested
    @DisplayName("advance")
    class Advance {

        @Test
        @DisplayName("steps 가 0 이면 시작일 그대로")
        void zeroStepsReturnsStartDate() {
            for (Cadence cadence : Cadence.values()) {
                assertThat(cadence.advance(MONDAY, 0)).isEqualTo(MONDAY);
            }
        }

        @ParameterizedTest(name = "{0} × {1}회 → {2}")
        @CsvSource({
                "WEEKLY,   1, 2026-09-14",
                "WEEKLY,   4, 2026-10-05",
                "BIWEEKLY, 1, 2026-09-21",
                "BIWEEKLY, 3, 2026-10-19",
                "MONTHLY,  1, 2026-10-07",
                "MONTHLY,  6, 2027-03-07",
        })
        @DisplayName("주기별로 정확히 진행한다")
        void advancesByCadence(Cadence cadence, int steps, LocalDate expected) {
            assertThat(cadence.advance(MONDAY, steps)).isEqualTo(expected);
        }

        @Test
        @DisplayName("월 주기는 30일 근사가 아니다 — 12회차에도 같은 일자를 유지한다")
        void monthlyDoesNotDriftLikeThirtyDayApproximation() {
            // 30일 근사였다면 12회차에서 2027-09-02 로 닷새 앞당겨진다.
            assertThat(Cadence.MONTHLY.advance(MONDAY, 12)).isEqualTo(LocalDate.of(2027, 9, 7));
        }

        @Test
        @DisplayName("월 주기의 말일은 해당 월의 마지막 날로 맞춰진다")
        void monthlyClampsEndOfMonth() {
            LocalDate jan31 = LocalDate.of(2026, 1, 31);
            assertThat(Cadence.MONTHLY.advance(jan31, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(Cadence.MONTHLY.advance(jan31, 3)).isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("음수 steps 는 거부한다")
        void rejectsNegativeSteps() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Cadence.WEEKLY.advance(MONDAY, -1))
                    .withMessageContaining("주기 진행 수는 0 이상");
        }

        @Test
        @DisplayName("null 시작일은 거부한다")
        void rejectsNullFrom() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Cadence.WEEKLY.advance(null, 1))
                    .withMessage("from");
        }
    }

    @Test
    @DisplayName("valueOf 로도 모든 상수에 접근할 수 있다")
    void valueOfCoversAllConstants() {
        for (Cadence cadence : Cadence.values()) {
            assertThat(Cadence.valueOf(cadence.name())).isSameAs(cadence);
        }
    }
}
