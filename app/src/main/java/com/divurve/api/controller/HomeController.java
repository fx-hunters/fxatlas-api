package com.divurve.api.controller;

import com.divurve.api.dto.forecast.EventsResponse.Event;
import com.divurve.api.dto.home.HomeSummaryResponse;
import com.divurve.api.dto.home.HomeSummaryResponse.ActiveGoalDto;
import com.divurve.api.dto.home.HomeSummaryResponse.AttentionDto;
import com.divurve.api.dto.home.HomeSummaryResponse.BlockDto;
import com.divurve.api.dto.home.HomeSummaryResponse.FxStatusDto;
import com.divurve.api.dto.home.HomeSummaryResponse.ForecastDto;
import com.divurve.api.dto.home.HomeSummaryResponse.GoalsRouteDto;
import com.divurve.api.dto.home.HomeSummaryResponse.Interval80Dto;
import com.divurve.api.dto.home.HomeSummaryResponse.ProfileFitDto;
import com.divurve.api.dto.home.HomeSummaryResponse.TodayDto;
import com.divurve.api.config.auth.CurrentUser;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.common.response.Meta;
import com.divurve.domain.home.HomeSummaryService;
import com.divurve.domain.home.HomeSummaryService.HomeSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 요약 엔드포인트 (API 명세 v2 §5.11, 요구사항 v2 §4.4 FR-HM-01~08, 이슈 #54(7.5)).
 * 화면 v2 §11 6블록을 <b>고정 순서</b>로 반환한다 — 프로필·설정은 마이페이지로 분리한다(FR-HM-08).
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "홈 화면 6블록 요약")
public class HomeController {

    private final HomeSummaryService homeSummaryService;

    public HomeController(HomeSummaryService homeSummaryService) {
        this.homeSummaryService = homeSummaryService;
    }

    @Operation(
            summary = "홈 요약 6블록 조회",
            description = "오늘의 핵심·위험성향 Fit·외화현황·목표 영역·주의필요·Forecast 요약을 "
                    + "고정 순서로 반환한다. 데이터가 없는 블록도 생략하지 않고 state 로만 구분한다"
                    + "(filled/empty/route_pending/not_measured).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "홈 요약"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @GetMapping("/summary")
    public ApiResponse<HomeSummaryResponse> getSummary(@CurrentUser UUID userId) {
        HomeSummaryView view = homeSummaryService.getSummary(userId);
        Meta meta = Meta.mock(view.referenceTime()).withRegime(view.regime());
        return ApiResponse.of(toResponse(view), meta);
    }

    private HomeSummaryResponse toResponse(HomeSummaryView view) {
        return new HomeSummaryResponse(
                view.blocks().stream()
                        .map(block -> new BlockDto(block.order(), block.key(), block.state()))
                        .toList(),
                new TodayDto(view.today().headlineCode(), view.today().badge()),
                new ProfileFitDto(view.profileFit().grade(), view.profileFit().concentrationStatus()),
                new FxStatusDto(
                        view.fxStatus().fxRatio(),
                        view.fxStatus().topCurrencyCode(),
                        view.fxStatus().sensitivity1pctKrw(),
                        view.fxStatus().dayChangeKrw()),
                new GoalsRouteDto(
                        view.goalsRoute().activeGoals().stream()
                                .map(goal -> new ActiveGoalDto(
                                        goal.id(), goal.name(), goal.currencyCode(), goal.targetAmount(),
                                        goal.targetDate(), goal.status()))
                                .toList(),
                        view.goalsRoute().routeEnabled()),
                new AttentionDto(
                        view.attention().regimeBadge(),
                        view.attention().upcomingEvents().stream()
                                .map(event -> new Event(
                                        event.date(), event.title(), event.currencyCode(), event.importance()))
                                .toList()),
                view.forecast() == null
                        ? null
                        : new ForecastDto(
                                view.forecast().pairCode(),
                                view.forecast().currentRate(),
                                new Interval80Dto(
                                        view.forecast().interval80().lo(), view.forecast().interval80().hi())));
    }
}
