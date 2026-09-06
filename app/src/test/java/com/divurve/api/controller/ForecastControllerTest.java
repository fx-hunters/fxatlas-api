package com.divurve.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.divurve.domain.forecast.ForecastService;
import com.divurve.domain.port.FxRateHistoryProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ForecastService forecastService;

    private ForecastService.ForecastData mockForecastData;
    private List<ForecastService.ForecastFactor> mockFactors;
    private ForecastService.ModelPerformanceData mockModelPerf;
    private List<ForecastService.EconomicEventData> mockEvents;

    @BeforeEach
    void setUp() {
        // Mock forecast data
        mockForecastData = new ForecastService.ForecastData(
            "USD_KRW",
            30,
            1200.0,
            createBaseline(),
            createHistory(),
            createPathPoints(),
            0.15,
            50,
            "Normal"
        );

        // Mock factors
        mockFactors = List.of(
            new ForecastService.ForecastFactor("fed_rate", "Federal Reserve Rate", 0.35, "Bullish"),
            new ForecastService.ForecastFactor("risk_sentiment", "Risk Sentiment", -0.25, "Bearish"),
            new ForecastService.ForecastFactor("gdp", "GDP Growth", 0.15, "Neutral")
        );

        // Mock model performance
        mockModelPerf = new ForecastService.ModelPerformanceData(
            "USD_KRW",
            30,
            0.62,
            15.5,
            0.78,
            45.3,
            0.48,
            18.2
        );

        // Mock events
        mockEvents = List.of(
            new ForecastService.EconomicEventData(
                LocalDate.now().plusDays(3).toString(),
                "Fed Rate Decision",
                "USD",
                "High"
            )
        );
    }

    @Test
    void testGetForecast_Success() throws Exception {
        when(forecastService.getForecast("USD_KRW", 30)).thenReturn(mockForecastData);

        mockMvc.perform(get("/api/v1/forecast")
                .param("pairCode", "USD_KRW")
                .param("horizon", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pairCode").value("USD_KRW"))
            .andExpect(jsonPath("$.data.horizonDays").value(30))
            .andExpect(jsonPath("$.data.currentRate").value(1200.0));

        verify(forecastService, times(1)).getForecast("USD_KRW", 30);
    }

    @Test
    void testGetFactors_Success() throws Exception {
        when(forecastService.getFactors("USD_KRW")).thenReturn(mockFactors);

        mockMvc.perform(get("/api/v1/forecast/factors")
                .param("pairCode", "USD_KRW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pairCode").value("USD_KRW"))
            .andExpect(jsonPath("$.data.factors").isArray())
            .andExpect(jsonPath("$.data.factors.length()").value(3));

        verify(forecastService, times(1)).getFactors("USD_KRW");
    }

    @Test
    void testGetModelPerformance_Success() throws Exception {
        when(forecastService.getModelPerformance("USD_KRW", 30)).thenReturn(mockModelPerf);

        mockMvc.perform(get("/api/v1/forecast/model-performance")
                .param("pairCode", "USD_KRW")
                .param("horizon", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pairCode").value("USD_KRW"))
            .andExpect(jsonPath("$.data.horizonDays").value(30))
            .andExpect(jsonPath("$.data.model.hitRate").value(0.62));

        verify(forecastService, times(1)).getModelPerformance("USD_KRW", 30);
    }

    @Test
    void testGetEvents_Success() throws Exception {
        when(forecastService.getEvents()).thenReturn(mockEvents);

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events").isArray())
            .andExpect(jsonPath("$.data.events.length()").value(1))
            .andExpect(jsonPath("$.data.events[0].title").value("Fed Rate Decision"));

        verify(forecastService, times(1)).getEvents();
    }

    private List<Double> createBaseline() {
        List<Double> baseline = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            baseline.add(1200.0);
        }
        return baseline;
    }

    private List<FxRateHistoryProvider.HistoryRateSnapshot> createHistory() {
        List<FxRateHistoryProvider.HistoryRateSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            history.add(new FxRateHistoryProvider.HistoryRateSnapshot(
                LocalDate.now().minusDays(100 - i),
                1200.0 + Math.sin(i * 0.05) * 10
            ));
        }
        return history;
    }

    private List<com.divurve.engine.forecast.FanChartCalculator.PathPoint> createPathPoints() {
        List<com.divurve.engine.forecast.FanChartCalculator.PathPoint> points = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            points.add(new com.divurve.engine.forecast.FanChartCalculator.PathPoint(
                1180.0 + i * 0.5,
                1220.0 + i * 0.5,
                1150.0 + i * 0.5,
                1250.0 + i * 0.5
            ));
        }
        return points;
    }
}
