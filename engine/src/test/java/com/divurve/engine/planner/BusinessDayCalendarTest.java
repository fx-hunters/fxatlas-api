package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BusinessDayCalendar} — 마감 버퍼의 영업일 계산 (플래너 명세 §9.4·§21-5).
 */
@DisplayName("BusinessDayCalendar")
class BusinessDayCalendarTest {

    private BusinessDayCalendar calendar;

    @BeforeEach
    void setUp() {
        calendar = new BusinessDayCalendar();
    }

    @Test
    @DisplayName("평일은 영업일이다")
    void isBusinessDay_Weekday_ReturnsTrue() {
        // 2026-09-09 은 수요일
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 9, 9))).isTrue();
    }

    @Test
    @DisplayName("토요일은 영업일이 아니다")
    void isBusinessDay_Saturday_ReturnsFalse() {
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 9, 12))).isFalse();
    }

    @Test
    @DisplayName("일요일은 영업일이 아니다")
    void isBusinessDay_Sunday_ReturnsFalse() {
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 9, 13))).isFalse();
    }

    @Test
    @DisplayName("영업일 0일은 기준일을 그대로 돌려준다")
    void minusBusinessDays_Zero_ReturnsSameDate() {
        LocalDate date = LocalDate.of(2026, 9, 12); // 토요일이라도 그대로
        assertThat(calendar.minusBusinessDays(date, 0)).isEqualTo(date);
    }

    @Test
    @DisplayName("주 중간에서 3영업일을 빼면 같은 주 안에 머문다")
    void minusBusinessDays_WithinWeek_StaysInSameWeek() {
        // 금 2026-09-11 에서 3영업일 → 화 2026-09-08
        assertThat(calendar.minusBusinessDays(LocalDate.of(2026, 9, 11), 3))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    @DisplayName("주말을 건너뛰고 센다")
    void minusBusinessDays_SkipsWeekend() {
        // 화 2026-09-08 에서 3영업일 → 월 7일(1), 금 4일(2), 목 3일(3). 토·일은 세지 않는다
        assertThat(calendar.minusBusinessDays(LocalDate.of(2026, 9, 8), 3))
                .isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("학비 버퍼 5영업일도 주말을 건너뛴다")
    void minusBusinessDays_TuitionBuffer_SkipsWeekend() {
        // 수 2026-09-09 에서 5영업일 → 수 2026-09-02
        assertThat(calendar.minusBusinessDays(LocalDate.of(2026, 9, 9), 5))
                .isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    @DisplayName("결과는 항상 영업일이다")
    void minusBusinessDays_ResultIsAlwaysBusinessDay() {
        LocalDate start = LocalDate.of(2026, 9, 14); // 월요일
        for (int days = 1; days <= 10; days++) {
            assertThat(calendar.isBusinessDay(calendar.minusBusinessDays(start, days))).isTrue();
        }
    }

    @Test
    @DisplayName("음수 영업일은 거부한다")
    void minusBusinessDays_Negative_Throws() {
        assertThatThrownBy(() -> calendar.minusBusinessDays(LocalDate.of(2026, 9, 9), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("기준일이 null 이면 거부한다")
    void minusBusinessDays_NullDate_Throws() {
        assertThatThrownBy(() -> calendar.minusBusinessDays(null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("판정 대상이 null 이면 거부한다")
    void isBusinessDay_Null_Throws() {
        assertThatThrownBy(() -> calendar.isBusinessDay(null))
                .isInstanceOf(NullPointerException.class);
    }
}
