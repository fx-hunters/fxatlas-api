package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.home.HomeSummaryResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.forecast.ForecastService.EconomicEventView;
import com.divurve.domain.home.HomeSummaryService;
import com.divurve.domain.home.HomeSummaryService.ActiveGoalView;
import com.divurve.domain.home.HomeSummaryService.AttentionView;
import com.divurve.domain.home.HomeSummaryService.BlockView;
import com.divurve.domain.home.HomeSummaryService.ForecastSummaryView;
import com.divurve.domain.home.HomeSummaryService.FxStatusView;
import com.divurve.domain.home.HomeSummaryService.GoalsRouteView;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import com.divurve.domain.home.HomeSummaryService.IntervalView;
import com.divurve.domain.home.HomeSummaryService.ProfileFitView;
import com.divurve.domain.home.HomeSummaryService.TodayView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link HomeController} 매핑 검증 (API 명세 v2 §5.11).
 *
 * <p>미인증 401 은 {@code CurrentUserArgumentResolverTest} 가 한 벌로 검증한다 (이슈 #50).
 */
@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private HomeSummaryService homeSummaryService;

    private final UUID userId = UUID.randomUUID();

    private HomeController controller() {
        return new HomeController(homeSummaryService);
    }

    private HomeSummaryView fullView(Instant now) {
        return new HomeSummaryView(
                List.of(
                        new BlockView(1, "today", "filled"),
                        new BlockView(2, "profile_fit", "filled"),
                        new BlockView(3, "fx_status", "filled"),
                        new BlockView(4, "goals_route", "empty"),
                        new BlockView(5, "attention", "filled"),
                        new BlockView(6, "forecast", "filled")),
                new TodayView("vol_elevated_usd", "caution"),
                new ProfileFitView("balanced", "above_threshold"),
                new FxStatusView(0.361, "USD", 247_200L, 84_000L),
                new GoalsRouteView(List.of(), "empty"),
                new AttentionView("caution", List.of(
                        new EconomicEventView(LocalDate.now().plusDays(3), "FOMC", "USD", "high"))),
                new ForecastSummaryView("USDKRW", 1382.40, new IntervalView(1346.0, 1431.0)),
                "elevated",
                now);
    }

    @Test
    void getSummary_은_6블록을_순서대로_매핑한다() {
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(fullView(now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        HomeSummaryResponse data = response.data();
        assertThat(data.blocks()).extracting(HomeSummaryResponse.BlockDto::key).containsExactly(
                "today", "profile_fit", "fx_status", "goals_route", "attention", "forecast");
        assertThat(data.today().headlineCode()).isEqualTo("vol_elevated_usd");
        assertThat(data.today().badge()).isEqualTo("caution");
        assertThat(data.profileFit().grade()).isEqualTo("balanced");
        assertThat(data.fxStatus().fxRatio()).isEqualTo(0.361);
        assertThat(data.fxStatus().topCurrencyCode()).isEqualTo("USD");
        assertThat(data.attention().upcomingEvents()).hasSize(1);
        assertThat(data.forecast().pairCode()).isEqualTo("USDKRW");
        assertThat(data.forecast().interval80().lo()).isEqualTo(1346.0);
    }

    @Test
    void getSummary_은_meta_regime을_반영한다() {
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(fullView(now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        assertThat(response.meta().regime()).isEqualTo("elevated");
    }

    @Test
    void getSummary_forecast가_null이면_응답도_null이다() {
        Instant now = Instant.now();
        HomeSummaryView view = new HomeSummaryView(
                List.of(new BlockView(6, "forecast", "empty")),
                new TodayView("regime_normal", "normal"),
                new ProfileFitView(null, "unknown"),
                new FxStatusView(0.0, null, 0L, null),
                new GoalsRouteView(List.of(), "empty"),
                new AttentionView("normal", List.of()),
                null,
                "normal",
                now);
        when(homeSummaryService.getSummary(userId)).thenReturn(view);

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        assertThat(response.data().forecast()).isNull();
    }

    @Test
    void getSummary_활성목표가_있으면_목표목록을_매핑한다() {
        Instant now = Instant.now();
        HomeSummaryView view = new HomeSummaryView(
                List.of(new BlockView(4, "goals_route", "filled")),
                new TodayView("regime_normal", "normal"),
                new ProfileFitView("balanced", "within_threshold"),
                new FxStatusView(0.2, "USD", 10_000L, null),
                new GoalsRouteView(
                        List.of(new ActiveGoalView(
                                "goal-1", "여행자금", "USD", 1000.0, LocalDate.now().plusMonths(6), "active")),
                        "filled"),
                new AttentionView("normal", List.of()),
                new ForecastSummaryView("USDKRW", 1382.40, new IntervalView(1346.0, 1431.0)),
                "normal",
                now);
        when(homeSummaryService.getSummary(userId)).thenReturn(view);

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        assertThat(response.data().goalsRoute().activeGoals()).hasSize(1);
        assertThat(response.data().goalsRoute().activeGoals().get(0).name()).isEqualTo("여행자금");
    }
}
