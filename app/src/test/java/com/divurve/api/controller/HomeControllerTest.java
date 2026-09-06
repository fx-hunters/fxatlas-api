package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.home.HomeSummaryResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.home.HomeSummaryService;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import com.divurve.domain.home.HomeSummaryService.TodayAction;
import com.divurve.domain.home.HomeSummaryService.CurrencyStatus;
import com.divurve.domain.home.HomeSummaryService.Notice;
import com.divurve.domain.home.HomeSummaryService.WeeklyChange;
import com.divurve.domain.home.HomeSummaryService.MarketSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link HomeController} 매핑 검증.
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

    @Test
    void getSummary_은_홈요약을_래핑한다() {
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(
                new HomeSummaryView(
                        new TodayAction("100,000 KRW"),
                        new CurrencyStatus(3),
                        new Notice("특이사항 없음"),
                        new WeeklyChange("상승"),
                        new MarketSummary("안정적"),
                        now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        HomeSummaryResponse data = response.data();
        assertThat(data.todayAction().heroAmount()).isEqualTo("100,000 KRW");
        assertThat(data.currencyStatus().totalAssets()).isEqualTo(3);
        assertThat(data.notice().message()).isEqualTo("특이사항 없음");
        assertThat(data.weeklyChange().summary()).isEqualTo("상승");
        assertThat(data.marketSummary().summary()).isEqualTo("안정적");
        assertThat(data.referenceTime()).isEqualTo(now);
    }

    @Test
    void getSummary_은_올바른_필드를_매핑한다() {
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(
                new HomeSummaryView(
                        new TodayAction("50,000 USD"),
                        new CurrencyStatus(5),
                        new Notice("재검토 필요"),
                        new WeeklyChange("상승 추세"),
                        new MarketSummary("변동성 높음"),
                        now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        HomeSummaryResponse data = response.data();
        assertThat(data.todayAction().heroAmount()).isEqualTo("50,000 USD");
        assertThat(data.currencyStatus().totalAssets()).isEqualTo(5);
        assertThat(data.notice().message()).isEqualTo("재검토 필요");
        assertThat(data.weeklyChange().summary()).isEqualTo("상승 추세");
        assertThat(data.marketSummary().summary()).isEqualTo("변동성 높음");
    }

    @Test
    void getSummary_은_응답을_ApiResponse로_래핑한다() {
        Instant now = Instant.now();
        when(homeSummaryService.getSummary(userId)).thenReturn(
                new HomeSummaryView(
                        new TodayAction("0 KRW"),
                        new CurrencyStatus(0),
                        new Notice(""),
                        new WeeklyChange(""),
                        new MarketSummary(""),
                        now));

        ApiResponse<HomeSummaryResponse> response = controller().getSummary(userId);

        assertThat(response.data()).isNotNull();
        assertThat(response.meta()).isNotNull();
    }
}
