package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.GoalService;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanAllocationGuard} — 보유 외화 중복 배정 방지 (플래너 명세 §8, 불변조건 §21-7).
 *
 * <p>핵심은 <b>목표를 하나씩 보면 통과하는데 합치면 보유량을 넘는</b> 경우다. 그 상태를 놓치면
 * 사용자는 실제로 없는 외화를 가진 것처럼 계획을 받는다.
 */
@DisplayName("PlanAllocationGuard")
class PlanAllocationGuardTest {

    private static final UUID OWNER_ID = UUID.randomUUID();

    private GoalRepository goalRepository;
    private GoalService goalService;
    private PlanAllocationGuard guard;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        goalService = mock(GoalService.class);
        guard = new PlanAllocationGuard(goalRepository, goalService);
    }

    private Goal goalWithAllocation(String currencyCode, double allocated) {
        Goal goal = Goal.builder(
                        User.createDemo("a@b.com", "사용자"), "목표", "onetime", "spend", currencyCode)
                .allocatedHoldingAmount(allocated)
                .build();
        goal.setIdForTest(UUID.randomUUID());
        return goal;
    }

    @Test
    @DisplayName("보유량 안에서 배정하면 통과한다")
    void withinHolding_Passes() {
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(5000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID)).thenReturn(List.of());

        assertThatCode(() -> guard.requireAllocatable(OWNER_ID, "USD", 3000.0, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("보유량을 넘겨 배정하면 거부한다 — 명세 §8")
    void exceedsHolding_Throws() {
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(1000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireAllocatable(OWNER_ID, "USD", 3000.0, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("field", "allocated_holding_amount");
    }

    @Test
    @DisplayName("다른 목표가 이미 가져간 몫을 뺀 나머지만 배정할 수 있다 — 불변조건 §21-7")
    void countsOtherGoalsAllocation() {
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(5000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID))
                .thenReturn(List.of(goalWithAllocation("USD", 4000.0)));

        // 각각은 보유량 이하지만 합치면 6000 > 5000 이다
        assertThatThrownBy(() -> guard.requireAllocatable(OWNER_ID, "USD", 2000.0, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("다른 목표에");
    }

    @Test
    @DisplayName("남은 몫과 정확히 같으면 통과한다 — 경계 포함")
    void exactRemainder_Passes() {
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(5000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID))
                .thenReturn(List.of(goalWithAllocation("USD", 4000.0)));

        assertThatCode(() -> guard.requireAllocatable(OWNER_ID, "USD", 1000.0, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 통화의 배정은 세지 않는다")
    void otherCurrencyIsIgnored() {
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(3000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID))
                .thenReturn(List.of(goalWithAllocation("JPY", 100000.0)));

        assertThatCode(() -> guard.requireAllocatable(OWNER_ID, "USD", 3000.0, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자기 자신의 기존 배정은 빼고 센다 — 목표를 수정할 때 자기 몫에 막히면 안 된다")
    void excludesOwnGoal() {
        Goal existing = goalWithAllocation("USD", 4000.0);
        when(goalService.getHeldAmountByCurrency(OWNER_ID, "USD")).thenReturn(5000.0);
        when(goalRepository.findByOwner_Id(OWNER_ID)).thenReturn(List.of(existing));

        assertThatCode(() -> guard.requireAllocatable(OWNER_ID, "USD", 4500.0, existing.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배정액이 0이면 검사하지 않는다 — 보유 외화를 쓰지 않는 계획이다")
    void zeroAllocation_SkipsCheck() {
        assertThatCode(() -> guard.requireAllocatable(OWNER_ID, "USD", 0.0, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(goalService, goalRepository);
    }

    @Test
    @DisplayName("null 인자와 의존은 거부한다")
    void nullArguments_Throw() {
        assertThatThrownBy(() -> new PlanAllocationGuard(null, goalService))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlanAllocationGuard(goalRepository, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guard.requireAllocatable(null, "USD", 1.0, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guard.requireAllocatable(OWNER_ID, null, 1.0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
