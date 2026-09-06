package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RoundScheduleGenerator} — 회차 날짜 생성 (플래너 명세 §9.4·§10.1·§21-4).
 */
@DisplayName("RoundScheduleGenerator")
class RoundScheduleGeneratorTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 2);

    private RoundScheduleGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RoundScheduleGenerator();
    }

    @Test
    @DisplayName("주간 주기로 종료일까지 회차를 만든다")
    void generate_Weekly_CoversUntilEndDate() {
        List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 3, 30), Cadence.WEEKLY);

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 3, 30));
    }

    @Test
    @DisplayName("종료일 당일은 포함한다")
    void generate_EndDateItself_IsIncluded() {
        List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 3, 9), Cadence.WEEKLY);

        assertThat(dates).last().isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("모든 회차는 계획 종료일 이전이다 — 불변조건 §21-4")
    void generate_AllDates_AreOnOrBeforeEndDate() {
        LocalDate endDate = LocalDate.of(2026, 5, 20);

        List<LocalDate> dates = generator.generate(START, endDate, Cadence.BIWEEKLY);

        assertThat(dates).isNotEmpty().allSatisfy(date -> assertThat(date).isBeforeOrEqualTo(endDate));
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 회차를 만들지 않는다")
    void generate_EndBeforeStart_ReturnsEmpty() {
        assertThat(generator.generate(START, START.minusDays(1), Cadence.WEEKLY)).isEmpty();
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 회차는 하나다")
    void generate_SameDay_ReturnsSingleRound() {
        assertThat(generator.generate(START, START, Cadence.MONTHLY)).containsExactly(START);
    }

    @Test
    @DisplayName("정기형 점검 기간까지 월간 회차를 만든다")
    void generateForHorizon_SixMonths_Monthly() {
        List<LocalDate> dates = generator.generateForHorizon(START, 6, Cadence.MONTHLY);

        assertThat(dates).hasSize(7)
                .first().isEqualTo(START);
        assertThat(dates).last().isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    @DisplayName("점검 기간이 1개월 미만이면 거부한다")
    void generateForHorizon_ZeroMonths_Throws() {
        assertThatThrownBy(() -> generator.generateForHorizon(START, 0, Cadence.MONTHLY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개월 이상");
    }

    @Test
    @DisplayName("null 인자는 거부한다")
    void nullArguments_Throw() {
        assertThatThrownBy(() -> generator.generate(null, START, Cadence.WEEKLY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> generator.generate(START, null, Cadence.WEEKLY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> generator.generate(START, START, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> generator.generateForHorizon(null, 3, Cadence.MONTHLY))
                .isInstanceOf(NullPointerException.class);
    }
}
