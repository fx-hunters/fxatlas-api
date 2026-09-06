package com.divurve.domain.home;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HomeSummaryServiceTest {

    private UserRepository userRepository;
    private HoldingService holdingService;
    private DepositService depositService;
    private HomeSummaryService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        holdingService = Mockito.mock(HoldingService.class);
        depositService = Mockito.mock(DepositService.class);
        service = new HomeSummaryService(userRepository, holdingService, depositService);
    }

    @Test
    void testGetSummary_ReturnsHomeSummaryView() {
        User user = User.create("test@example.com", "테스트사용자", false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingService.list(userId)).thenReturn(List.of());
        when(depositService.list(userId)).thenReturn(List.of());

        HomeSummaryView summary = service.getSummary(userId);

        assertThat(summary).isNotNull();
        assertThat(summary.referenceTime()).isNotNull();
        assertThat(summary.currencyStatus().totalAssets()).isGreaterThanOrEqualTo(0);
        assertThat(summary.notice().message()).isNotEmpty();
    }

    @Test
    void testGetSummary_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void testGetSummary_ContainsAllRequiredFields() {
        User user = User.create("test@example.com", "테스트사용자", false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingService.list(userId)).thenReturn(List.of());
        when(depositService.list(userId)).thenReturn(List.of());

        HomeSummaryView summary = service.getSummary(userId);

        assertThat(summary.todayAction()).isNotNull();
        assertThat(summary.currencyStatus()).isNotNull();
        assertThat(summary.notice()).isNotNull();
        assertThat(summary.weeklyChange()).isNotNull();
        assertThat(summary.marketSummary()).isNotNull();
    }
}
