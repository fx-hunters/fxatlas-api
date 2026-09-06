package com.divurve.domain.forecast;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.divurve.domain.port.EconomicEventProvider;
import com.divurve.domain.port.FxRateHistoryProvider;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.MacroIndicatorProvider;
import com.divurve.domain.port.RateSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private FxRateProvider fxRateProvider;

    @Mock
    private FxRateHistoryProvider historyProvider;

    @Mock
    private MacroIndicatorProvider macroProvider;

    @Mock
    private EconomicEventProvider eventProvider;

    private ForecastService service;

    @BeforeEach
    void setUp() {
        service = new ForecastService(
            fxRateProvider,
            historyProvider,
            macroProvider,
            eventProvider
        );
    }

    @Test
    void testGetForecast_HappyPath() {
        String pairCode = "USD_KRW";
        int horizon = 30;

        // Mock 데이터
        RateSnapshot currentRate = new RateSnapshot(
            pairCode,
            new BigDecimal("1200.00"),
            LocalDate.now(),
            "ECOS",
            Instant.now()
        );
        when(fxRateProvider.fetchLatest(pairCode)).thenReturn(currentRate);

        // 과거 환율 데이터 (최소 5년 + 30일)
        List<FxRateHistoryProvider.HistoryRateSnapshot> history = createHistoryData(5 * 252 + 30);
        when(historyProvider.fetchHistorical(eq(pairCode), any(LocalDate.class), anyInt()))
            .thenReturn(history);

        // 실행
        ForecastService.ForecastData result = service.getForecast(pairCode, horizon);

        // 검증
        assertNotNull(result);
        assertEquals(pairCode, result.pairCode());
        assertEquals(horizon, result.horizonDays());
        assertEquals(1200.0, result.currentRate(), 1e-6);
        assertNotNull(result.pathPoints());
        assertTrue(result.realized30d() > 0);
        assertTrue(result.percentile5y() >= 0 && result.percentile5y() <= 100);
        assertNotNull(result.regime());
    }

    @Test
    void testGetForecast_InvalidHorizon() {
        assertThrows(IllegalArgumentException.class, () -> service.getForecast("USD_KRW", 50));
        assertThrows(IllegalArgumentException.class, () -> service.getForecast("USD_KRW", 15));
    }

    @Test
    void testGetForecast_NullPairCode() {
        assertThrows(NullPointerException.class, () -> service.getForecast(null, 30));
    }

    @Test
    void testGetFactors_HappyPath() {
        String pairCode = "USD_KRW";

        List<ForecastService.ForecastFactor> result = service.getFactors(pairCode);

        assertNotNull(result);
        assertEquals(3, result.size()); // 상위 3개
        assertTrue(result.stream().allMatch(f -> f.key() != null && !f.key().isEmpty()));
        assertTrue(result.stream().allMatch(f -> f.label() != null && !f.label().isEmpty()));
    }

    @Test
    void testGetFactors_NullPairCode() {
        assertThrows(NullPointerException.class, () -> service.getFactors(null));
    }

    @Test
    void testGetModelPerformance_HappyPath() {
        String pairCode = "USD_JPY";
        int horizon = 90;

        ForecastService.ModelPerformanceData result = service.getModelPerformance(pairCode, horizon);

        assertNotNull(result);
        assertEquals(pairCode, result.pairCode());
        assertEquals(horizon, result.horizonDays());
        assertTrue(result.modelHitRate() >= 0 && result.modelHitRate() <= 1.0);
        assertTrue(result.modelMae() > 0);
        assertTrue(result.coverage80() >= 0 && result.coverage80() <= 1.0);
        assertTrue(result.avgWidth() > 0);
    }

    @Test
    void testGetModelPerformance_InvalidHorizon() {
        assertThrows(IllegalArgumentException.class, () -> service.getModelPerformance("USD_KRW", 60));
    }

    @Test
    void testGetEvents_HappyPath() {
        List<EconomicEventProvider.EconomicEvent> events = List.of(
            new EconomicEventProvider.EconomicEvent(
                LocalDate.now().plusDays(5),
                "Fed Rate Decision",
                "USD",
                "High"
            )
        );
        when(eventProvider.fetchUpcoming(any(LocalDate.class), anyInt())).thenReturn(events);

        List<ForecastService.EconomicEventData> result = service.getEvents();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Fed Rate Decision", result.get(0).title());
    }

    @Test
    void testExtendRatesByTriangulation_HappyPath() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);
        baseRates.put("USD_JPY", 110.0);
        baseRates.put("EUR_USD", 1.1);

        Map<String, Double> result = service.extendRatesByTriangulation(baseRates);

        assertNotNull(result);
        assertTrue(result.containsKey("EUR_KRW"));
        assertTrue(result.containsKey("EUR_JPY"));
        assertEquals(1320.0, result.get("EUR_KRW"), 1e-6);
        assertEquals(121.0, result.get("EUR_JPY"), 1e-6);
    }

    @Test
    void testExtendRatesByTriangulation_MissingRequired() {
        Map<String, Double> baseRates = new HashMap<>();
        baseRates.put("USD_KRW", 1200.0);

        assertThrows(IllegalArgumentException.class,
            () -> service.extendRatesByTriangulation(baseRates));
    }

    private List<FxRateHistoryProvider.HistoryRateSnapshot> createHistoryData(int count) {
        List<FxRateHistoryProvider.HistoryRateSnapshot> history = new ArrayList<>();
        double baseRate = 1200.0;
        for (int i = 0; i < count; i++) {
            double rate = baseRate + (Math.sin(i * 0.05) * 10); // 약간의 변동성 추가
            history.add(new FxRateHistoryProvider.HistoryRateSnapshot(
                LocalDate.now().minusDays(count - i),
                rate
            ));
        }
        return history;
    }
}
