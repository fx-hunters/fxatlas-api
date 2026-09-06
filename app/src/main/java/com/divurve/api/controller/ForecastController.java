package com.divurve.api.controller;

import com.divurve.api.dto.forecast.EventsResponse;
import com.divurve.api.dto.forecast.FactorsResponse;
import com.divurve.api.dto.forecast.ForecastResponse;
import com.divurve.api.dto.forecast.ModelPerformanceResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.forecast.ForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환율 범위(전망·동인·성적표·일정) 엔드포인트 (명세 2·3.5·3.6장).
 *
 * <p>팬차트, 전망 동인, 모델 성적표, 경제 일정 조회를 제공한다.
 * FR-FC-10: 이 화면은 행동(매수/매도 제안)을 제시하지 않고 전망만 표시한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Forecast", description = "팬차트·전망 동인·모델 성적표·경제 일정")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = Objects.requireNonNull(forecastService);
    }

    @Operation(summary = "팬차트·구간·변동성", description = "통화쌍별 30/90일 팬차트 데이터")
    @GetMapping("/forecast")
    public ApiResponse<ForecastResponse> getForecast(
            @Parameter(description = "통화쌍 (USD_KRW, USD_JPY, EUR_USD 등)", example = "USD_KRW")
            @RequestParam String pairCode,
            @Parameter(description = "미래 지평 (일수)", example = "30")
            @RequestParam int horizon) {

        ForecastService.ForecastData data = forecastService.getForecast(pairCode, horizon);
        ForecastResponse response = mapToForecastResponse(data);

        return ApiResponse.of(response);
    }

    @Operation(summary = "전망 동인", description = "상위 3개 전망 동인 (참고용)")
    @GetMapping("/forecast/factors")
    public ApiResponse<FactorsResponse> getFactors(
            @Parameter(description = "통화쌍 (USD_KRW, USD_JPY, EUR_USD 등)", example = "USD_KRW")
            @RequestParam String pairCode) {

        List<ForecastService.ForecastFactor> factors = forecastService.getFactors(pairCode);
        FactorsResponse response = mapToFactorsResponse(pairCode, factors);

        return ApiResponse.of(response);
    }

    @Operation(summary = "모델 성적표", description = "방향 적중률, MAE, 구간 포함률 등")
    @GetMapping("/forecast/model-performance")
    public ApiResponse<ModelPerformanceResponse> getModelPerformance(
            @Parameter(description = "통화쌍 (USD_KRW, USD_JPY, EUR_USD 등)", example = "USD_KRW")
            @RequestParam String pairCode,
            @Parameter(description = "미래 지평 (일수)", example = "30")
            @RequestParam int horizon) {

        ForecastService.ModelPerformanceData data = forecastService.getModelPerformance(pairCode, horizon);
        ModelPerformanceResponse response = mapToModelPerformanceResponse(data);

        return ApiResponse.of(response);
    }

    @Operation(summary = "경제 일정", description = "향후 90일 경제 이벤트")
    @GetMapping("/events")
    public ApiResponse<EventsResponse> getEvents() {

        List<ForecastService.EconomicEventData> events = forecastService.getEvents();
        EventsResponse response = mapToEventsResponse(events);

        return ApiResponse.of(response);
    }

    // ── 응답 매핑 ────────────────────────────────────

    private ForecastResponse mapToForecastResponse(ForecastService.ForecastData data) {
        List<ForecastResponse.History> history = data.history().stream()
            .map(h -> new ForecastResponse.History(
                h.date().toString(),
                h.rate()
            ))
            .toList();

        List<ForecastResponse.PathPoint> paths = data.pathPoints().stream()
            .map(p -> new ForecastResponse.PathPoint(
                "", // TODO: 날짜 추가 필요
                p.p50Lo(),
                p.p50Hi(),
                p.p80Lo(),
                p.p80Hi()
            ))
            .toList();

        List<ForecastResponse.ModelPoint> modelPath = List.of(); // TODO: 모델 경로 추가

        double p80Lo = data.pathPoints().isEmpty()
            ? data.currentRate()
            : data.pathPoints().get(data.pathPoints().size() - 1).p80Lo();
        double p80Hi = data.pathPoints().isEmpty()
            ? data.currentRate()
            : data.pathPoints().get(data.pathPoints().size() - 1).p80Hi();

        ForecastResponse.Interval interval80 = new ForecastResponse.Interval(
            p80Lo,
            p80Hi,
            (p80Hi - p80Lo) / data.currentRate(),
            0.0 // TODO: 3년 평균 대비 계산 필요
        );

        ForecastResponse.Volatility volatility = new ForecastResponse.Volatility(
            data.realized30d(),
            data.percentile5y(),
            data.regime()
        );

        ForecastResponse.UserImpact userImpact = new ForecastResponse.UserImpact(
            0L, // TODO: 사용자 자산 영향 계산
            0L
        );

        return new ForecastResponse(
            data.pairCode(),
            data.horizonDays(),
            data.currentRate(),
            data.baseline().get(0),
            history,
            paths,
            modelPath,
            interval80,
            volatility,
            userImpact,
            "이 전망은 과거 데이터 및 통계 모형 기반이며, 미래를 보장하지 않습니다."
        );
    }

    private FactorsResponse mapToFactorsResponse(String pairCode, List<ForecastService.ForecastFactor> factors) {
        List<FactorsResponse.Factor> factorList = factors.stream()
            .map(f -> new FactorsResponse.Factor(
                f.key(),
                f.label(),
                f.contribution(),
                f.direction()
            ))
            .toList();

        return new FactorsResponse(pairCode, factorList);
    }

    private ModelPerformanceResponse mapToModelPerformanceResponse(ForecastService.ModelPerformanceData data) {
        ModelPerformanceResponse.Model model = new ModelPerformanceResponse.Model(
            data.modelHitRate(),
            data.modelMae(),
            data.coverage80(),
            data.avgWidth()
        );

        ModelPerformanceResponse.RandomWalk randomWalk = new ModelPerformanceResponse.RandomWalk(
            data.rwHitRate(),
            data.rwMae()
        );

        ModelPerformanceResponse.Validation validation = new ModelPerformanceResponse.Validation(
            "Walk-forward validation",
            5,
            true
        );

        return new ModelPerformanceResponse(
            data.pairCode(),
            data.horizonDays(),
            model,
            randomWalk,
            validation,
            "모델은 과거 5년 데이터 기반 워크포워드 검증 결과입니다."
        );
    }

    private EventsResponse mapToEventsResponse(List<ForecastService.EconomicEventData> events) {
        List<EventsResponse.Event> eventList = events.stream()
            .map(e -> new EventsResponse.Event(
                e.date(),
                e.title(),
                e.currencyCode(),
                e.importance()
            ))
            .toList();

        return new EventsResponse(eventList);
    }
}
