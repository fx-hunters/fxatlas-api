package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RoundScheduleGenerator} 테스트 — 회차 날짜 생성 (명세 §9.4·§10.1·§21-4).
 *
 * <p><b>§21-4</b> 모든 회차는 계획 종료일 이전(당일 포함)이다.
 */
@DisplayName("RoundScheduleGenerator")
class RoundScheduleGeneratorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 7);

    private final RoundScheduleGenerator generator = new RoundScheduleGenerator();

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("주간 회차를 종료일까지 만든다")
        void weeklyRounds() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 10, 5), Cadence.WEEKLY);

            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 21),
                    LocalDate.of(2026, 9, 28),
                    LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("격주 회차를 만든다")
        void biweeklyRounds() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 10, 5), Cadence.BIWEEKLY);

            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 21),
                    LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("월간 회차를 만든다")
        void monthlyRounds() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 12, 31), Cadence.MONTHLY);

            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 10, 7),
                    LocalDate.of(2026, 11, 7),
                    LocalDate.of(2026, 12, 7));
        }

        @Test
        @DisplayName("모든 회차가 종료일 이하다 (§21-4)")
        void allRoundsAreOnOrBeforeEndDate() {
            LocalDate endDate = LocalDate.of(2027, 3, 3);

            for (Cadence cadence : Cadence.values()) {
                List<LocalDate> dates = generator.generate(START, endDate, cadence);

                assertThat(dates)
                        .as("%s", cadence)
                        .isNotEmpty()
                        .allSatisfy(date -> assertThat(date).isBeforeOrEqualTo(endDate));
            }
        }

        @Test
        @DisplayName("종료일 당일에 떨어지는 회차는 포함한다")
        void includesRoundOnEndDate() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 9, 14), Cadence.WEEKLY);

            assertThat(dates).endsWith(LocalDate.of(2026, 9, 14));
        }

        @Test
        @DisplayName("종료일 하루 전이면 그 회차는 빠진다")
        void excludesRoundAfterEndDate() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2026, 9, 13), Cadence.WEEKLY);

            assertThat(dates).containsExactly(START);
        }

        @Test
        @DisplayName("시작일과 종료일이 같으면 회차는 하나다")
        void sameStartAndEndGivesOneRound() {
            assertThat(generator.generate(START, START, Cadence.WEEKLY)).containsExactly(START);
        }

        @Test
        @DisplayName("시작일이 종료일을 넘으면 빈 목록이다 — 호출부가 검증 실패로 다룬다 (§8)")
        void startAfterEndGivesEmptyList() {
            List<LocalDate> dates = generator.generate(START, START.minusDays(1), Cadence.WEEKLY);

            assertThat(dates).isEmpty();
        }

        @Test
        @DisplayName("날짜는 오름차순이다")
        void datesAreAscending() {
            List<LocalDate> dates = generator.generate(START, LocalDate.of(2027, 9, 7), Cadence.MONTHLY);

            assertThat(dates).isSorted();
        }

        @Test
        @DisplayName("null 인자는 각각 거부한다")
        void rejectsNulls() {
            assertThatNullPointerException()
                    .isThrownBy(() -> generator.generate(null, START, Cadence.WEEKLY))
                    .withMessage("startDate");
            assertThatNullPointerException()
                    .isThrownBy(() -> generator.generate(START, null, Cadence.WEEKLY))
                    .withMessage("endDate");
            assertThatNullPointerException()
                    .isThrownBy(() -> generator.generate(START, START, null))
                    .withMessage("cadence");
        }
    }

    @Nested
    @DisplayName("generateForHorizon")
    class GenerateForHorizon {

        @Test
        @DisplayName("점검 기간까지 월간 회차를 만든다 (§10.1)")
        void monthlyOverHorizon() {
            List<LocalDate> dates = generator.generateForHorizon(START, 6, Cadence.MONTHLY);

            // startDate 부터 startDate+6개월 까지 포함하므로 7회차다.
            assertThat(dates).hasSize(7);
            assertThat(dates).first().isEqualTo(START);
            assertThat(dates).last().isEqualTo(LocalDate.of(2027, 3, 7));
        }

        @Test
        @DisplayName("점검 기간 안의 주간 회차를 만든다")
        void weeklyOverHorizon() {
            List<LocalDate> dates = generator.generateForHorizon(START, 1, Cadence.WEEKLY);

            assertThat(dates).allSatisfy(date ->
                    assertThat(date).isBeforeOrEqualTo(START.plusMonths(1)));
            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 21),
                    LocalDate.of(2026, 9, 28),
                    LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("점검 기간이 0 이면 거부한다")
        void rejectsZeroHorizon() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> generator.generateForHorizon(START, 0, Cadence.MONTHLY))
                    .withMessageContaining("점검 기간은 1개월 이상");
        }

        @Test
        @DisplayName("점검 기간이 음수면 거부한다")
        void rejectsNegativeHorizon() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> generator.generateForHorizon(START, -1, Cadence.MONTHLY))
                    .withMessageContaining("점검 기간은 1개월 이상");
        }

        @Test
        @DisplayName("null 시작일은 거부한다")
        void rejectsNullStartDate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> generator.generateForHorizon(null, 6, Cadence.MONTHLY))
                    .withMessage("startDate");
        }
    }

    @Test
    @DisplayName("마감형: 목표일에서 버퍼를 뺀 종료일까지만 회차를 만든다 (§9.4)")
    void deadlinePlanEndsBeforeTargetDate() {
        BusinessDayCalendar calendar = new BusinessDayCalendar();
        LocalDate targetDate = LocalDate.of(2026, 12, 25);
        LocalDate planEndDate =
                calendar.minusBusinessDays(targetDate, PlannerPolicy.businessDayBufferFor("travel"));

        List<LocalDate> dates = generator.generate(START, planEndDate, Cadence.MONTHLY);

        assertThat(dates).allSatisfy(date -> assertThat(date).isBefore(targetDate));
        assertThat(dates.get(dates.size() - 1)).isBeforeOrEqualTo(planEndDate);
    }
}
