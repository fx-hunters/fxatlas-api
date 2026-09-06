package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.goal.GoalCreateRequest;
import com.divurve.api.dto.goal.GoalListResponse;
import com.divurve.api.dto.goal.GoalResponse;
import com.divurve.api.dto.goal.GoalUpdateRequest;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.GoalService;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.route.RouteFeatureFlag;
import com.divurve.domain.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalController 테스트")
class GoalControllerTest {

    @Mock
    private GoalService goalService;

    /** route.enabled=true — Route 확정 이후의 동작을 검증하는 컨트롤러. */
    private GoalController goalController;

    /** route.enabled=false — 기본값. 쓰기 3종은 501, 목록은 빈 배열이어야 한다. */
    private GoalController disabledController;

    private UUID currentUserId;
    private User owner;

    @BeforeEach
    void setUp() {
        goalController = new GoalController(goalService, new RouteFeatureFlag(true));
        disabledController = new GoalController(goalService, new RouteFeatureFlag(false));
        currentUserId = UUID.randomUUID();
        owner = User.createDemo("test@example.com", "Test User");
    }

    @Test
    @DisplayName("목표 목록 조회 성공")
    void listGoalsSuccess() {
        Goal goal1 = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();
        goal1.setIdForTest(UUID.randomUUID());
        Goal goal2 = Goal.builder(owner, "EUR 목표", "recurring", "invest", "EUR")
                .targetAmount(5000.0)
                .status("active")
                .build();
        goal2.setIdForTest(UUID.randomUUID());

        when(goalService.listByOwner(currentUserId)).thenReturn(List.of(goal1, goal2));
        when(goalService.getHeldAmountByCurrency(currentUserId, "USD")).thenReturn(5000.0);
        when(goalService.getHeldAmountByCurrency(currentUserId, "EUR")).thenReturn(2000.0);

        ApiResponse<GoalListResponse> response = goalController.listGoals(currentUserId);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().goals()).hasSize(2);
        assertThat(response.data().routeEnabled()).isTrue();
    }

    @Test
    @DisplayName("목표 목록 조회 (빈 결과)")
    void listGoalsEmpty() {
        when(goalService.listByOwner(currentUserId)).thenReturn(List.of());

        ApiResponse<GoalListResponse> response = goalController.listGoals(currentUserId);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().goals()).isEmpty();
        assertThat(response.data().routeEnabled()).isTrue();
    }

    @Test
    @DisplayName("route.enabled=false 면 목록은 501 이 아니라 빈 배열 + route_enabled=false 다 (명세 v2 §3·§6.2)")
    void listGoalsReturnsEmptyStateWhenRouteDisabled() {
        ApiResponse<GoalListResponse> response = disabledController.listGoals(currentUserId);

        assertThat(response.data().goals()).isEmpty();
        assertThat(response.data().routeEnabled()).isFalse();
        // 조회 자체를 하지 않는다 — 목표 데이터를 읽지 않고 빈 상태를 돌려준다.
        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("route.enabled=false 면 목표 생성은 501 (요구사항 v2 §4.12 미확정)")
    void createGoalReturns501WhenRouteDisabled() {
        GoalCreateRequest request = new GoalCreateRequest(
                "USD 목표", "deadline", "travel", "USD", 10000.0,
                LocalDate.of(2026, 12, 31), null, 0, "KRW", null, false);

        assertThatThrownBy(() -> disabledController.createGoal(currentUserId, request))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("route.enabled=false 면 목표 수정은 501")
    void updateGoalReturns501WhenRouteDisabled() {
        GoalUpdateRequest request = new GoalUpdateRequest(
                "수정된 목표", 20000.0, LocalDate.of(2027, 12, 31), 200000L, "year", true);

        assertThatThrownBy(() -> disabledController.updateGoal(
                currentUserId, UUID.randomUUID().toString(), request))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("route.enabled=false 면 목표 삭제는 501")
    void deleteGoalReturns501WhenRouteDisabled() {
        assertThatThrownBy(() -> disabledController.deleteGoal(
                currentUserId, UUID.randomUUID().toString()))
                .isInstanceOf(NotImplementedException.class);
        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("목표 상세 조회는 플래그와 무관하게 동작한다 — 계산이 아닌 단순 조회다")
    void getGoalIsNotGatedByRouteFlag() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();
        goal.setIdForTest(goalId);
        when(goalService.getByIdAndOwner(currentUserId, goalId)).thenReturn(goal);
        when(goalService.getHeldAmountByCurrency(currentUserId, "USD")).thenReturn(1.0);

        ApiResponse<GoalResponse> response =
                disabledController.getGoal(currentUserId, goalId.toString());

        assertThat(response.data().name()).isEqualTo("USD 목표");
    }

    @Test
    @DisplayName("목표 생성 성공")
    void createGoalSuccess() {
        GoalCreateRequest request = new GoalCreateRequest(
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

        Goal createdGoal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .targetDate(LocalDate.of(2026, 12, 31))
                .budgetAmount(0)
                .budgetCurrencyCode("KRW")
                .isSpeculative(false)
                .status("active")
                .build();
        createdGoal.setIdForTest(UUID.randomUUID());

        when(goalService.create(
                currentUserId,
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
                false)).thenReturn(createdGoal);
        when(goalService.getHeldAmountByCurrency(currentUserId, "USD")).thenReturn(0.0);

        ApiResponse<GoalResponse> response = goalController.createGoal(currentUserId, request);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().name()).isEqualTo("USD 목표");
        assertThat(response.data().currencyCode()).isEqualTo("USD");
        assertThat(response.data().status()).isEqualTo("active");
    }

    @Test
    @DisplayName("목표 상세 조회 성공")
    void getGoalSuccess() {
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder(owner, "USD 목표", "deadline", "travel", "USD")
                .targetAmount(10000.0)
                .status("active")
                .build();
        goal.setIdForTest(goalId);

        when(goalService.getByIdAndOwner(currentUserId, goalId)).thenReturn(goal);
        when(goalService.getHeldAmountByCurrency(currentUserId, "USD")).thenReturn(5000.0);

        ApiResponse<GoalResponse> response = goalController.getGoal(currentUserId, goalId.toString());

        assertThat(response.data()).isNotNull();
        assertThat(response.data().name()).isEqualTo("USD 목표");
        assertThat(response.data().heldAmount()).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("목표 수정 성공")
    void updateGoalSuccess() {
        UUID goalId = UUID.randomUUID();
        GoalUpdateRequest request = new GoalUpdateRequest(
                "수정된 목표",
                20000.0,
                LocalDate.of(2027, 12, 31),
                200000L,
                "year",
                true);

        Goal updatedGoal = Goal.builder(owner, "수정된 목표", "deadline", "travel", "USD")
                .targetAmount(20000.0)
                .targetDate(LocalDate.of(2027, 12, 31))
                .budgetAmount(200000)
                .budgetPeriod("year")
                .isSpeculative(true)
                .status("active")
                .build();
        updatedGoal.setIdForTest(goalId);

        when(goalService.update(
                currentUserId,
                goalId,
                "수정된 목표",
                20000.0,
                LocalDate.of(2027, 12, 31),
                200000L,
                "year",
                true)).thenReturn(updatedGoal);
        when(goalService.getHeldAmountByCurrency(currentUserId, "USD")).thenReturn(5000.0);

        ApiResponse<GoalResponse> response = goalController.updateGoal(
                currentUserId,
                goalId.toString(),
                request);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().name()).isEqualTo("수정된 목표");
        assertThat(response.data().targetAmount()).isEqualTo(20000.0);
    }

    @Test
    @DisplayName("목표 삭제 성공")
    void deleteGoalSuccess() {
        UUID goalId = UUID.randomUUID();

        doNothing().when(goalService).delete(currentUserId, goalId);

        ApiResponse<Void> response = goalController.deleteGoal(currentUserId, goalId.toString());

        assertThat(response).isNotNull();
    }
}
