package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link BusinessDayCalendar} 테스트 — 마감 버퍼의 영업일 계산 (명세 §9.4·§21-5).
 *
 * <p>공휴일 테이블이 도입되면 이 테스트의 기대값 상당수가 바뀐다. 그때 커밋 타입은
 * {@code calc} 이며 변경 전/후 날짜를 본문에 남긴다.
 */
@DisplayName("BusinessDayCalendar")
class BusinessDayCalendarTest {

    /** 2026-09-07 은 월요일이다. 이 주의 요일 배치를 기준으로 기대값을 잡았다. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    private final BusinessDayCalendar calendar = new BusinessDayCalendar();

    @Nested
    @DisplayName("isBusinessDay")
    class IsBusinessDay {

        @Test
        @DisplayName("월~금은 영업일이다")
        void weekdaysAreBusinessDays() {
            for (int offset = 0; offset < 5; offset++) {
                LocalDate weekday = MONDAY.plusDays(offset);
                assertThat(calendar.isBusinessDay(weekday))
                        .as("%s (%s)", weekday, weekday.getDayOfWeek())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("토·일은 영업일이 아니다")
        void weekendIsNotBusinessDay() {
            assertThat(calendar.isBusinessDay(SATURDAY)).isFalse();
            assertThat(calendar.isBusinessDay(SUNDAY)).isFalse();
        }

        @Test
        @DisplayName("공휴일은 아직 제외하지 않는다 — 주말 여부만 본다")
        void holidaysAreNotYetExcluded() {
            // 2026-01-01 은 목요일이자 신정이다. 공휴일 테이블이 없어 현재는 영업일로 취급된다.
            LocalDate newYearsDay = LocalDate.of(2026, 1, 1);
            assertThat(newYearsDay.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
            assertThat(calendar.isBusinessDay(newYearsDay)).isTrue();
        }

        @Test
        @DisplayName("null 날짜는 거부한다")
        void rejectsNullDate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calendar.isBusinessDay(null))
                    .withMessage("date");
        }
    }

    @Nested
    @DisplayName("minusBusinessDays")
    class MinusBusinessDays {

        @ParameterizedTest(name = "{0} - {1}영업일 = {2}")
        @CsvSource({
                // 화요일에서 1영업일 앞은 월요일 — 주말을 건너지 않는 단순 경우
                "2026-09-08, 1, 2026-09-07",
                // 월요일에서 1영업일 앞은 금요일 — 주말 2일을 건너뛴다
                "2026-09-07, 1, 2026-09-04",
                // 명세 §9.4 기본 버퍼 3영업일: 목요일 → 월요일
                "2026-09-10, 3, 2026-09-07",
                // 기본 버퍼 3영업일이 주말을 가로지르는 경우: 화요일 → 목요일(전주)
                "2026-09-08, 3, 2026-09-03",
                // 학비 버퍼 5영업일: 목요일 → 목요일(전주), 정확히 한 주
                "2026-09-10, 5, 2026-09-03",
                // 여러 주를 가로지르는 경우: 10영업일 = 2주
                "2026-09-10, 10, 2026-08-27",
        })
        @DisplayName("주말을 건너뛰고 영업일만 센다 (§21-5)")
        void skipsWeekends(LocalDate from, int days, LocalDate expected) {
            assertThat(calendar.minusBusinessDays(from, days)).isEqualTo(expected);
        }

        @Test
        @DisplayName("결과는 항상 영업일이다 — days 가 1 이상이면")
        void resultIsAlwaysBusinessDay() {
            for (int offset = 0; offset < 14; offset++) {
                for (int days = 1; days <= 5; days++) {
                    LocalDate result = calendar.minusBusinessDays(MONDAY.plusDays(offset), days);
                    assertThat(calendar.isBusinessDay(result))
                            .as("%s - %d영업일 = %s", MONDAY.plusDays(offset), days, result)
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("days 가 0 이면 기준일을 그대로 돌려준다")
        void zeroDaysReturnsSameDate() {
            assertThat(calendar.minusBusinessDays(MONDAY, 0)).isEqualTo(MONDAY);
        }

        @Test
        @DisplayName("days 가 0 이고 기준일이 주말이면 주말을 그대로 돌려준다")
        void zeroDaysOnWeekendReturnsWeekend() {
            // 거슬러 올라갈 영업일이 없으므로 보정하지 않는다.
            // 호출부가 버퍼 0 을 쓰는 경우는 없지만(§9.4 는 최소 3), 동작을 기록해 둔다.
            assertThat(calendar.minusBusinessDays(SATURDAY, 0)).isEqualTo(SATURDAY);
        }

        @Test
        @DisplayName("주말에서 출발해도 영업일만 센다")
        void startingFromWeekend() {
            // 토요일에서 1영업일 앞은 금요일이다.
            assertThat(calendar.minusBusinessDays(SATURDAY, 1)).isEqualTo(LocalDate.of(2026, 9, 11));
            // 일요일에서 1영업일 앞도 금요일이다.
            assertThat(calendar.minusBusinessDays(SUNDAY, 1)).isEqualTo(LocalDate.of(2026, 9, 11));
        }

        @Test
        @DisplayName("음수 영업일은 거부한다")
        void rejectsNegativeDays() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calendar.minusBusinessDays(MONDAY, -1))
                    .withMessageContaining("영업일 수는 0 이상");
        }

        @Test
        @DisplayName("null 기준일은 거부한다")
        void rejectsNullDate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calendar.minusBusinessDays(null, 3))
                    .withMessage("date");
        }
    }

    @Test
    @DisplayName("목표일에서 목적별 버퍼를 빼면 계획 종료일이 된다 (§9.4)")
    void planEndDateIsTargetDateMinusPurposeBuffer() {
        LocalDate targetDate = LocalDate.of(2026, 12, 25);

        LocalDate travelEnd = calendar.minusBusinessDays(
                targetDate, PlannerPolicy.businessDayBufferFor("travel"));
        LocalDate tuitionEnd = calendar.minusBusinessDays(
                targetDate, PlannerPolicy.businessDayBufferFor("tuition"));

        // 2026-12-25 는 금요일. 3영업일 앞은 화요일, 5영업일 앞은 금요일(전주)이다.
        assertThat(travelEnd).isEqualTo(LocalDate.of(2026, 12, 22));
        assertThat(tuitionEnd).isEqualTo(LocalDate.of(2026, 12, 18));
        assertThat(tuitionEnd).isBefore(travelEnd);
    }
}
