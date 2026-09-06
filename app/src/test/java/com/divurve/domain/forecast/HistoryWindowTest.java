package com.divurve.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link HistoryWindow} — 영업일 → 달력일 환산 (이슈 #57).
 *
 * <p>이 환산이 빠져서 5년치(1,290 영업일)를 1,290 달력일로 요청했고, 실제로는 약 880개만 돌아와
 * {@code /forecast} 가 500 으로 실패했다. 회귀를 막는 것이 이 테스트의 목적이다.
 */
class HistoryWindowTest {

    /** 5년 백분위 + 30일 롤링에 필요한 관측 수 — 두 UseCase 가 공통으로 쓰는 값. */
    private static final int FIVE_YEAR_OBSERVATIONS = 5 * HistoryWindow.BUSINESS_DAYS_PER_YEAR + 30;

    @Test
    @DisplayName("환산 결과는 항상 요청한 영업일 수보다 크다 — 같은 숫자를 그대로 쓰면 관측이 모자란다")
    void 환산값은_영업일_수보다_크다() {
        assertThat(HistoryWindow.calendarDaysFor(FIVE_YEAR_OBSERVATIONS))
                .isGreaterThan(FIVE_YEAR_OBSERVATIONS);
    }

    @Test
    @DisplayName("5년 구간은 실제 영업일 관측 1,290개를 확보할 만큼 넓다")
    void 오년_구간은_필요_관측을_확보한다() {
        int calendarDays = HistoryWindow.calendarDaysFor(FIVE_YEAR_OBSERVATIONS);

        // 주말만 제외해도(공휴일 제외 전) 여유가 있어야 한다. 한국 공휴일은 연 15일 내외.
        double weekdays = calendarDays / 7.0 * 5;
        double holidays = calendarDays / (double) HistoryWindow.CALENDAR_DAYS_PER_YEAR * 15;

        assertThat(weekdays - holidays).isGreaterThan(FIVE_YEAR_OBSERVATIONS);
    }

    @Test
    @DisplayName("1,290 영업일은 1,290 달력일이 아니다 — 회귀 방지")
    void 단위를_혼동하지_않는다() {
        assertThat(HistoryWindow.calendarDaysFor(1290)).isNotEqualTo(1290);
    }

    @Test
    @DisplayName("환산은 올림한다 — 1 영업일도 최소 2 달력일")
    void 올림한다() {
        assertThat(HistoryWindow.calendarDaysFor(1)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -1290})
    @DisplayName("영업일 수가 0 이하면 거부한다")
    void 양수가_아니면_거부한다(int businessDays) {
        assertThatThrownBy(() -> HistoryWindow.calendarDaysFor(businessDays))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessDays must be positive");
    }

    @Test
    @DisplayName("유틸리티 클래스는 인스턴스화할 수 없다")
    void 인스턴스화할_수_없다() throws NoSuchMethodException {
        Constructor<HistoryWindow> constructor = HistoryWindow.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }
}
