package com.divurve.domain.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.safemode.SafeModeCheckResult;
import com.divurve.engine.safemode.SafeModeEvaluator;
import java.math.BigDecimal;
import java.time.Instant;
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
@DisplayName("SafeModeService 테스트")
class SafeModeServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private FxRateProvider fxRateProvider;

    @Mock
    private SafeModeEvaluator safeModeEvaluator;

    private SafeModeService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SafeModeService(
            goalRepository,
            planRepository,
            holdingRepository,
            depositRepository,
            fxRateProvider,
            safeModeEvaluator);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("evaluateSafeMode null userId 예외")
    void testEvaluateSafeModeNullUserId() {
        assertThrows(NullPointerException.class, () -> service.evaluateSafeMode(null));
    }

    @Test
    @DisplayName("사용자 목표 없을 때 빈 목록으로 평가")
    void testEvaluateWithNoGoals() {
        when(goalRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(fxRateProvider.fetchLatest("USD_KRW"))
            .thenReturn(new RateSnapshot("USD_KRW", new BigDecimal("1200"), LocalDate.now(), "ECOS", Instant.now()));
        when(safeModeEvaluator.evaluate(any()))
            .thenReturn(new SafeModeCheckResult(false, "normal", List.of()));

        SafeModeCheckResult result = service.evaluateSafeMode(userId);

        assertNotNull(result);
        assertFalse(result.active());
        assertEquals("normal", result.status());
        verify(goalRepository, times(1)).findByOwner_Id(userId);
    }

    @Test
    @DisplayName("FxRateProvider 예외 발생 시 null로 처리")
    void testEvaluateWithFxProviderException() {
        when(goalRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(fxRateProvider.fetchLatest("USD_KRW"))
            .thenThrow(new RuntimeException("Network error"));
        when(safeModeEvaluator.evaluate(any()))
            .thenReturn(new SafeModeCheckResult(false, "normal", List.of()));

        SafeModeCheckResult result = service.evaluateSafeMode(userId);

        assertNotNull(result);
        verify(fxRateProvider, times(2)).fetchLatest("USD_KRW");
    }

    @Test
    @DisplayName("evaluator 결과를 그대로 반환")
    void testReturnEvaluatorResult() {
        var checks = List.of(
            new SafeModeCheckResult.Check("data_staleness", true, null),
            new SafeModeCheckResult.Check("volatility_high", false, "변동성 높음"));
        var expected = new SafeModeCheckResult(true, "caution", checks);

        when(goalRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(fxRateProvider.fetchLatest("USD_KRW"))
            .thenReturn(new RateSnapshot("USD_KRW", new BigDecimal("1200"), LocalDate.now(), "ECOS", Instant.now()));
        when(safeModeEvaluator.evaluate(any()))
            .thenReturn(expected);

        SafeModeCheckResult result = service.evaluateSafeMode(userId);

        assertEquals(expected.active(), result.active());
        assertEquals(expected.status(), result.status());
        assertEquals(expected.checks().size(), result.checks().size());
    }
}
