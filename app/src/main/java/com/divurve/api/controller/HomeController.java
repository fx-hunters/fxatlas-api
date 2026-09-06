package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.home.HomeSummaryResponse;
import com.divurve.api.dto.home.HomeSummaryResponse.CurrencyStatusDto;
import com.divurve.api.dto.home.HomeSummaryResponse.MarketSummaryDto;
import com.divurve.api.dto.home.HomeSummaryResponse.NoticeDto;
import com.divurve.api.dto.home.HomeSummaryResponse.TodayActionDto;
import com.divurve.api.dto.home.HomeSummaryResponse.WeeklyChangeDto;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.home.HomeSummaryService;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import com.divurve.domain.port.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 요약 엔드포인트 (이슈 #21, FR-HM-01~07).
 * 홈 화면의 오늘의 행동·외화현황·주의필요·주간변화·시장요약을 제공한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "홈 화면 요약")
public class HomeController {

    private final HomeSummaryService homeSummaryService;

    public HomeController(HomeSummaryService homeSummaryService) {
        this.homeSummaryService = homeSummaryService;
    }

    @Operation(summary = "홈 요약 조회")
    @GetMapping("/summary")
    public ApiResponse<HomeSummaryResponse> getSummary() {
        HomeSummaryView summary = homeSummaryService.getSummary(currentUserId());
        return ApiResponse.of(toHomeSummaryResponse(summary));
    }

    /** 현재 요청 주체의 사용자 id. 인증 컨텍스트가 없으면 401. */
    private UUID currentUserId() {
        return CurrentUserContext.get()
                .map(AuthPrincipal::userId)
                .orElseThrow(UnauthorizedException::new);
    }

    private HomeSummaryResponse toHomeSummaryResponse(HomeSummaryView view) {
        return new HomeSummaryResponse(
                new TodayActionDto(view.todayAction().heroAmount()),
                new CurrencyStatusDto(view.currencyStatus().totalAssets()),
                new NoticeDto(view.notice().message()),
                new WeeklyChangeDto(view.weeklyChange().summary()),
                new MarketSummaryDto(view.marketSummary().summary()),
                view.referenceTime());
    }
}
