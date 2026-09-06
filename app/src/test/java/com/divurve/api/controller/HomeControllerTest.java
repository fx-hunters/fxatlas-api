package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.home.HomeSummaryResponse;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.home.HomeSummaryService;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import com.divurve.domain.home.HomeSummaryService.TodayAction;
import com.divurve.domain.home.HomeSummaryService.CurrencyStatus;
import com.divurve.domain.home.HomeSummaryService.Notice;
import com.divurve.domain.home.HomeSummaryService.WeeklyChange;
import com.divurve.domain.home.HomeSummaryService.MarketSummary;
import com.divurve.domain.port.AuthPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link HomeController} 매핑 검증.
 */
@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private HomeSummaryService homeSummaryService;

    private final UUID userId = UUID.randomUUID();

    private HomeController controller() {
        return new HomeController(homeSummaryService);
    }

    private void authenticate() {
        CurrentUserContext.set(new AuthPrincipal(userId, false));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getSummary_은_홈요약을_래핑한다() {
        authenticate();
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(
                new HomeSummaryView(
                        new TodayAction("100,000 KRW"),
                        new CurrencyStatus(3),
                        new Notice("특이사항 없음"),
                        new WeeklyChange("상승"),
                        new MarketSummary("안정적"),
                        now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary();

        HomeSummaryResponse data = response.data();
        assertThat(data.todayAction().heroAmount()).isEqualTo("100,000 KRW");
        assertThat(data.currencyStatus().totalAssets()).isEqualTo(3);
        assertThat(data.notice().message()).isEqualTo("특이사항 없음");
        assertThat(data.weeklyChange().summary()).isEqualTo("상승");
        assertThat(data.marketSummary().summary()).isEqualTo("안정적");
        assertThat(data.referenceTime()).isEqualTo(now);
    }

    @Test
    void 인증_컨텍스트가_없으면_401() {
        assertThatThrownBy(() -> controller().getSummary())
                .isInstanceOf(UnauthorizedException.class);
    }
}
