package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.divurve.domain.forecast.ForecastService;
import com.divurve.domain.port.FxRateHistoryProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForecastControllerTest {

    private ForecastController controller;
    private ForecastService mockService;

    private ForecastService.ForecastData mockForecastData;
    private List<ForecastService.ForecastFactor> mockFactors;
    private ForecastService.ModelPerformanceData mockModelPerf;
    private List<ForecastService.EconomicEventData> mockEvents;

    @BeforeEach
    void setUp() {
        mockService = mock(ForecastService.class);
        controller = new ForecastController(mockService);

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
    void testGetForecast_Success() {
        when(mockService.getForecast("USD_KRW", 30)).thenReturn(mockForecastData);

        var response = controller.getForecast("USD_KRW", 30);

        assertNotNull(response);
        assertThat(response.data().pairCode()).isEqualTo("USD_KRW");
        assertThat(response.data().horizonDays()).isEqualTo(30);
        verify(mockService, times(1)).getForecast("USD_KRW", 30);
    }

    @Test
    void testGetFactors_Success() {
        when(mockService.getFactors("USD_KRW")).thenReturn(mockFactors);

        var response = controller.getFactors("USD_KRW");

        assertNotNull(response);
        assertThat(response.data().pairCode()).isEqualTo("USD_KRW");
        assertThat(response.data().factors()).hasSize(3);
        verify(mockService, times(1)).getFactors("USD_KRW");
    }

    @Test
    void testGetModelPerformance_Success() {
        when(mockService.getModelPerformance("USD_KRW", 30)).thenReturn(mockModelPerf);

        var response = controller.getModelPerformance("USD_KRW", 30);

        assertNotNull(response);
        assertThat(response.data().pairCode()).isEqualTo("USD_KRW");
        assertThat(response.data().horizonDays()).isEqualTo(30);
        verify(mockService, times(1)).getModelPerformance("USD_KRW", 30);
    }

    @Test
    void testGetEvents_Success() {
        when(mockService.getEvents()).thenReturn(mockEvents);

        var response = controller.getEvents();

        assertNotNull(response);
        assertThat(response.data().events()).hasSize(1);
        verify(mockService, times(1)).getEvents();
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

    private List<ForecastService.ForecastData.PathPoint> createPathPoints() {
        List<ForecastService.ForecastData.PathPoint> points = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            points.add(new ForecastService.ForecastData.PathPoint(
                "",
                1180.0 + i * 0.5,
                1220.0 + i * 0.5,
                1150.0 + i * 0.5,
                1250.0 + i * 0.5
            ));
        }
        return points;
    }
}
