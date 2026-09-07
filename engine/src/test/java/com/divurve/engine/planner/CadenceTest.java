package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Cadence} — 회차 주기 (플래너 명세 §5.2·§5.3).
 */
@DisplayName("Cadence")
class CadenceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    @Test
    @DisplayName("소문자 코드를 해석한다")
    void from_LowercaseCode_Resolves() {
        assertThat(Cadence.from("weekly")).isEqualTo(Cadence.WEEKLY);
        assertThat(Cadence.from("biweekly")).isEqualTo(Cadence.BIWEEKLY);
        assertThat(Cadence.from("monthly")).isEqualTo(Cadence.MONTHLY);
    }

    @Test
    @DisplayName("DB 에 저장된 대문자 코드도 해석한다")
    void from_UppercaseCode_Resolves() {
        assertThat(Cadence.from("MONTHLY")).isEqualTo(Cadence.MONTHLY);
    }

    @Test
    @DisplayName("알 수 없는 코드는 거부한다")
    void from_UnknownCode_Throws() {
        assertThatThrownBy(() -> Cadence.from("quarterly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 회차 주기");
    }

    @Test
    @DisplayName("코드가 null 이면 거부한다")
    void from_Null_Throws() {
        assertThatThrownBy(() -> Cadence.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("0 주기는 시작일 그대로다")
    void advance_ZeroSteps_ReturnsStart() {
        assertThat(Cadence.WEEKLY.advance(START, 0)).isEqualTo(START);
    }

    @Test
    @DisplayName("주간은 7일씩 진행한다")
    void advance_Weekly_AddsSevenDays() {
        assertThat(Cadence.WEEKLY.advance(START, 3)).isEqualTo(LocalDate.of(2026, 2, 5));
    }

    @Test
    @DisplayName("격주는 14일씩 진행한다")
    void advance_Biweekly_AddsFourteenDays() {
        assertThat(Cadence.BIWEEKLY.advance(START, 2)).isEqualTo(LocalDate.of(2026, 2, 12));
    }

    @Test
    @DisplayName("월간은 30일 근사가 아니라 같은 일자로 진행한다")
    void advance_Monthly_KeepsDayOfMonth() {
        assertThat(Cadence.MONTHLY.advance(START, 3)).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("월간에서 말일은 해당 월의 마지막 날로 맞춰진다")
    void advance_Monthly_ClampsEndOfMonth() {
        assertThat(Cadence.MONTHLY.advance(LocalDate.of(2026, 1, 31), 1))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("음수 주기는 거부한다")
    void advance_NegativeSteps_Throws() {
        assertThatThrownBy(() -> Cadence.MONTHLY.advance(START, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("시작일이 null 이면 거부한다")
    void advance_NullStart_Throws() {
        assertThatThrownBy(() -> Cadence.WEEKLY.advance(null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
