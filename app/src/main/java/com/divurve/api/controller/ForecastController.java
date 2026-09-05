package com.divurve.api.controller;

import com.divurve.api.dto.forecast.EventsResponse;
import com.divurve.api.dto.forecast.FactorsResponse;
import com.divurve.api.dto.forecast.ForecastResponse;
import com.divurve.api.dto.forecast.ModelPerformanceResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환율 범위(전망·동인·성적표·일정) 엔드포인트 스텁 (명세 2·3.5·3.6장).
 * 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Forecast", description = "팬차트·전망 동인·모델 성적표·경제 일정")
public class ForecastController {

    @Operation(summary = "팬차트·구간·변동성")
    @GetMapping("/forecast")
    public ApiResponse<ForecastResponse> getForecast(
            @RequestParam(required = false) String pairCode,
            @RequestParam(required = false) Integer horizon) {
        throw new NotImplementedException();
    }

    @Operation(summary = "전망 동인")
    @GetMapping("/forecast/factors")
    public ApiResponse<FactorsResponse> getFactors(@RequestParam(required = false) String pairCode) {
        throw new NotImplementedException();
    }

    @Operation(summary = "모델 성적표")
    @GetMapping("/forecast/model-performance")
    public ApiResponse<ModelPerformanceResponse> getModelPerformance(
            @RequestParam(required = false) String pairCode,
            @RequestParam(required = false) Integer horizon) {
        throw new NotImplementedException();
    }

    @Operation(summary = "경제 일정")
    @GetMapping("/events")
    public ApiResponse<EventsResponse> getEvents() {
        throw new NotImplementedException();
    }
}
