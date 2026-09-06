package com.divurve.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalService 테스트")
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HoldingService holdingService;

    @Mock
    private DepositService depositService;

    @Mock
    private User owner;

    private GoalService goalService;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, userRepository, holdingService, depositService);
        ownerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("목표 생성 성공")
    void createGoalSuccess() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> {
            Goal goal = invocation.getArgument(0);
            return goal;
        });

        Goal result = goalService.create(
                ownerId,
                "USD 목표",
                "deadline",
                "travel",
                "USD",
                10000.0,
                LocalDate.of(2026, 12, 31),
                null,
                0,
                "KRW",
                null,
                false);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("USD 목표");
        assertThat(result.getKind()).isEqualTo("deadline");
        assertThat(result.getCurrencyCode()).isEqualTo("USD");
        assertThat(result.getStatus()).isEqualTo("active");
    }

    @Test
    @DisplayName("목표 생성 시 사용자 미존재 예외")
    void createGoalUserNotFound() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.create(
                ownerId, "USD 목표", "deadline", "travel", "USD",
                10000.0, LocalDate.of(2026, 12, 31), null, 0, "KRW", null, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("소유자별 목표 목록 조회")
    void listByOwnerSuccess() {
        Goal goal1 = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();
        Goal goal2 = Goal.builder(owner, "EUR 목표", "recurring", "invest", "EUR")
                .targetAmount(5000.0)
                .status("active")
                .build();

        when(goalRepository.findByOwner_Id(ownerId)).thenReturn(List.of(goal1, goal2));

        List<Goal> results = goalService.listByOwner(ownerId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("USD 목표");
        assertThat(results.get(1).getName()).isEqualTo("EUR 목표");
    }

    @Test
    @DisplayName("소유자별 목표 목록 조회 (빈 결과)")
    void listByOwnerEmpty() {
        when(goalRepository.findByOwner_Id(ownerId)).thenReturn(List.of());

        List<Goal> results = goalService.listByOwner(ownerId);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("목표 단일 조회 성공")
    void getByIdAndOwnerSuccess() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(owner.getId()).thenReturn(ownerId);

        Goal result = goalService.getByIdAndOwner(ownerId, goalId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("USD 목표");
    }

    @Test
    @DisplayName("목표 조회 시 목표 미존재 예외")
    void getByIdAndOwnerNotFound() {
        UUID goalId = UUID.randomUUID();

        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getByIdAndOwner(ownerId, goalId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("목표를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("목표 조회 시 소유자 불일치 예외")
    void getByIdAndOwnerOwnerMismatch() {
        UUID goalId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(owner.getId()).thenReturn(otherOwnerId);

        assertThatThrownBy(() -> goalService.getByIdAndOwner(ownerId, goalId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("목표를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("목표 수정 성공")
    void updateGoalSuccess() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .budgetAmount(100000)
                .budgetPeriod("month")
                .isSpeculative(false)
                .status("active")
                .build();

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(owner.getId()).thenReturn(ownerId);

        Goal result = goalService.update(
                ownerId,
                goalId,
                "수정된 목표",
                20000.0,
                LocalDate.of(2027, 12, 31),
                200000L,
                "year",
                true);

        assertThat(result.getName()).isEqualTo("수정된 목표");
        assertThat(result.getTargetAmount()).isEqualTo(20000.0);
        assertThat(result.getTargetDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(result.getBudgetAmount()).isEqualTo(200000L);
        assertThat(result.getBudgetPeriod()).isEqualTo("year");
        assertThat(result.isSpeculative()).isTrue();
    }

    @Test
    @DisplayName("목표 수정 부분 필드만 업데이트")
    void updateGoalPartial() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .budgetAmount(100000)
                .isSpeculative(false)
                .status("active")
                .build();

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(owner.getId()).thenReturn(ownerId);

        Goal result = goalService.update(
                ownerId,
                goalId,
                "수정된 이름",
                null,
                null,
                null,
                null,
                null);

        assertThat(result.getName()).isEqualTo("수정된 이름");
        assertThat(result.getTargetAmount()).isEqualTo(10000.0);
        assertThat(result.getBudgetAmount()).isEqualTo(100000L);
        assertThat(result.isSpeculative()).isFalse();
    }

    @Test
    @DisplayName("목표 삭제 성공")
    void deleteGoalSuccess() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(owner.getId()).thenReturn(ownerId);
        doNothing().when(goalRepository).delete(goal);

        goalService.delete(ownerId, goalId);

        verify(goalRepository).delete(goal);
    }

    @Test
    @DisplayName("보유 외화금액 조회 성공")
    void getHeldAmountByCurrencySuccess() {
        Holding holding = Holding.create(owner, "AAPL", "USD", 100.0, 150.0);
        Deposit deposit = Deposit.create(owner, "USD", java.math.BigDecimal.valueOf(5000.0));

        when(holdingService.list(ownerId)).thenReturn(List.of(holding));
        when(depositService.list(ownerId)).thenReturn(List.of(deposit));

        double heldAmount = goalService.getHeldAmountByCurrency(ownerId, "USD");

        assertThat(heldAmount).isEqualTo(20000.0);
    }

    @Test
    @DisplayName("보유 외화금액 조회 (보유 없음)")
    void getHeldAmountByCurrencyEmpty() {
        when(holdingService.list(ownerId)).thenReturn(List.of());
        when(depositService.list(ownerId)).thenReturn(List.of());

        double heldAmount = goalService.getHeldAmountByCurrency(ownerId, "USD");

        assertThat(heldAmount).isEqualTo(0.0);
    }
}
