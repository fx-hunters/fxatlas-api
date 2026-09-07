package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.goal.GoalType;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanInput} — 저장된 목표에서 계산 입력을 만든다 (플래너 명세 §5).
 *
 * <p>목표 유형에 따라 <b>어느 필드를 읽는지가 달라진다</b>. 마감형은 {@code preferredCadence} 를,
 * 정기형은 {@code recurInterval} 을 주기로 쓴다 — 둘을 뒤바꾸면 회차 간격이 조용히 틀어진다.
 */
@DisplayName("PlanInput")
class PlanInputTest {

    private static User owner() {
        return User.createDemo("a@b.com", "사용자");
    }

    @Test
    @DisplayName("마감형은 preferredCadence 를 주기로 쓴다")
    void deadlineUsesPreferredCadence() {
        Goal goal = Goal.builder(owner(), "여행 자금", "onetime", "travel", "USD")
                .goalType(GoalType.DEADLINE)
                .targetAmount(4000.0)
                .targetDate(LocalDate.of(2026, 12, 24))
                .allocatedHoldingAmount(1000.0)
                .budgetAmount(1_000_000)
                .budgetPeriod("monthly")
                .preferredCadence("weekly")
                .recurInterval("monthly")
                .build();

        PlanInput input = PlanInput.from(goal);

        assertThat(input.isRecurring()).isFalse();
        assertThat(input.cadence()).isEqualTo("weekly");
        assertThat(input.targetAmount()).isEqualTo(4000.0);
        assertThat(input.allocatedHoldingAmount()).isEqualTo(1000.0);
        assertThat(input.budgetAmountKrw()).isEqualTo(1_000_000L);
        assertThat(input.budgetPeriod()).isEqualTo("monthly");
    }

    @Test
    @DisplayName("정기형은 recurInterval 을 주기로 쓴다")
    void recurringUsesRecurInterval() {
        Goal goal = Goal.builder(owner(), "ETF 자금", "onetime", "investment", "USD")
                .goalType(GoalType.RECURRING)
                .budgetAmount(500_000)
                .preferredCadence("weekly")
                .recurInterval("monthly")
                .recurStartDate(LocalDate.of(2026, 10, 1))
                .reviewHorizonMonths(6)
                .build();

        PlanInput input = PlanInput.from(goal);

        assertThat(input.isRecurring()).isTrue();
        assertThat(input.cadence()).isEqualTo("monthly");
        assertThat(input.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(input.reviewHorizonMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("예산이 0이면 미입력으로 본다 — 명세 §9.6 은 미입력을 별도 상태로 다룬다")
    void zeroBudgetIsTreatedAsAbsent() {
        Goal goal = Goal.builder(owner(), "여행 자금", "onetime", "travel", "USD")
                .goalType(GoalType.DEADLINE)
                .targetAmount(4000.0)
                .targetDate(LocalDate.of(2026, 12, 24))
                .budgetAmount(0)
                .build();

        assertThat(PlanInput.from(goal).budgetAmountKrw()).isNull();
    }
}
