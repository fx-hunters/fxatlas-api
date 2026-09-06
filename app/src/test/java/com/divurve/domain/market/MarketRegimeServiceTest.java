package com.divurve.domain.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.divurve.domain.forecast.CrossRateResolver;
import com.divurve.domain.forecast.PairCode;
import com.divurve.domain.port.FxRateHistoryProvider;
import com.divurve.engine.volatility.MarketChecks;
import com.divurve.engine.volatility.RegimeBadgeMapper;
import com.divurve.engine.volatility.RegimeClassifier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MarketRegimeService} 검증 (명세 v2 §5.10).
 *
 * <p>가장 중요한 불변식: <b>{@code keep_serving_forecast} 는 어떤 상태에서도 {@code true}</b> 다
 * (FR-SF-01). v1 안전모드는 여기서 503 을 냈다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketRegimeService")
class MarketRegimeServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final double BASE_RATE = 1382.40;
    private static final int LONG_HISTORY = 1400;

    @Mock
    private CrossRateResolver historyResolver;

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(
                historyResolver,
                new RegimeClassifier(),
                new RegimeBadgeMapper(),
                new MarketChecks(),
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("변동성이 상위 구간이면 배지는 turbulent 지만 산출은 계속된다")
    void turbulentStillServes() {
        givenHistory("USD_KRW", history(LONG_HISTORY, 0.004, 0.05, TODAY));
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertEquals("turbulent", view.badge());
        assertEquals("급변", view.badgeLabel());
        assertEquals("stress", view.regime());
        assertEquals(1, view.pairRegimes().size());
        assertEquals("stress", view.pairRegimes().get("USDKRW").regime());
        assertTrue(view.guidance().keepServingForecast());
        assertTrue(view.guidance().widenUncertainty());
        assertTrue(view.guidance().showPlanAssumptions());
    }

    @Test
    @DisplayName("평시에는 배지가 normal 이고 불확실성 안내를 넓히지 않는다")
    void calmMarket() {
        givenHistory("USD_KRW", history(LONG_HISTORY, 0.02, 0.0005, TODAY));
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertEquals("normal", view.badge());
        assertEquals("calm", view.regime());
        assertTrue(view.guidance().keepServingForecast());
        assertFalse(view.guidance().widenUncertainty());
        assertFalse(view.guidance().showPlanAssumptions());
        assertTrue(passed(view, MarketChecks.KEY_VOL_PERCENTILE));
        assertTrue(passed(view, MarketChecks.KEY_DATA_FRESHNESS));
        assertTrue(passed(view, MarketChecks.KEY_SOURCE_DIVERGENCE));
        assertFalse(view.anomaly().dataErrorDetected());
        assertEquals(MarketRegimeService.ANOMALY_NOTE, view.anomaly().note());
    }

    @Test
    @DisplayName("마지막 관측이 오래됐으면 데이터 오류로 표시하되 응답은 그대로 준다")
    void staleDataIsFlaggedNotBlocked() {
        LocalDate lastObservation = TODAY.minusDays(10);
        givenHistory("USD_KRW", history(LONG_HISTORY, 0.02, 0.0005, lastObservation));
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertFalse(passed(view, MarketChecks.KEY_DATA_FRESHNESS));
        assertTrue(view.anomaly().dataErrorDetected());
        assertTrue(view.guidance().keepServingForecast());
    }

    @Test
    @DisplayName("어떤 통화쌍도 조회되지 않으면 국면을 지어내지 않고 근거로 알린다")
    void noPairData() {
        givenUnsupported("USD_KRW");
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertTrue(view.pairRegimes().isEmpty());
        assertEquals("normal", view.badge());
        assertEquals("normal", view.regime());
        assertFalse(passed(view, MarketChecks.KEY_DATA_FRESHNESS));
        assertFalse(passed(view, MarketChecks.KEY_VOL_PERCENTILE));
        assertTrue(view.guidance().keepServingForecast());
    }

    @Test
    @DisplayName("관측이 모자란 통화쌍은 국면 없이 빠진다")
    void tooFewObservations() {
        givenHistory("USD_KRW", history(100, 0.01, 0.01, TODAY));
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertTrue(view.pairRegimes().isEmpty());
        assertTrue(view.guidance().keepServingForecast());
    }

    @Test
    @DisplayName("관측이 한 점뿐인 통화쌍도 국면 없이 빠진다")
    void singleObservation() {
        givenHistory("USD_KRW", history(1, 0.01, 0.01, TODAY));
        givenUnsupported("USD_JPY");
        givenUnsupported("EUR_USD");

        assertTrue(service.getRegime().pairRegimes().isEmpty());
    }

    @Test
    @DisplayName("여러 통화쌍이 있으면 가장 심각한 국면이 대표가 된다")
    void worstOfPairs() {
        // 관측 최신일을 일부러 어긋나게 둬 "가장 최근 관측"이 통화쌍 순서와 무관하게 잡히는지 본다.
        givenHistory("USD_KRW", history(LONG_HISTORY, 0.02, 0.0005, TODAY.minusDays(2)));
        givenHistory("USD_JPY", history(LONG_HISTORY, 0.004, 0.05, TODAY));
        givenHistory("EUR_USD", history(LONG_HISTORY, 0.02, 0.0005, TODAY.minusDays(1)));

        MarketRegimeService.MarketRegimeView view = service.getRegime();

        assertEquals(3, view.pairRegimes().size());
        assertEquals("calm", view.pairRegimes().get("USDKRW").regime());
        assertEquals("stress", view.pairRegimes().get("USDJPY").regime());
        assertEquals("calm", view.pairRegimes().get("EURUSD").regime());
        assertEquals("turbulent", view.badge());
        // 판정 근거는 백분위가 가장 높은 통화쌍으로 만든다.
        assertTrue(detail(view, MarketChecks.KEY_VOL_PERCENTILE).contains("USDJPY"));
        // 데이터 신선도는 가장 최근 관측 기준이라 통과한다.
        assertTrue(passed(view, MarketChecks.KEY_DATA_FRESHNESS));
    }

    // ── 픽스처 ───────────────────────────────────────────────────

    private void givenHistory(String providerCode, List<FxRateHistoryProvider.HistoryRateSnapshot> history) {
        when(historyResolver.fetch(eq(PairCode.parse(providerCode)), any(LocalDate.class), anyInt()))
                .thenReturn(history);
    }

    private void givenUnsupported(String providerCode) {
        when(historyResolver.fetch(eq(PairCode.parse(providerCode)), any(LocalDate.class), anyInt()))
                .thenThrow(new IllegalArgumentException("Unsupported pairCode: " + providerCode));
    }

    private static boolean passed(MarketRegimeService.MarketRegimeView view, String key) {
        return view.checks().stream()
                .filter(check -> check.key().equals(key))
                .findFirst()
                .orElseThrow()
                .passed();
    }

    private static String detail(MarketRegimeService.MarketRegimeView view, String key) {
        return view.checks().stream()
                .filter(check -> check.key().equals(key))
                .findFirst()
                .orElseThrow()
                .detail();
    }

    private static List<FxRateHistoryProvider.HistoryRateSnapshot> history(
            int size, double baseAmplitude, double recentAmplitude, LocalDate lastDate) {
        List<FxRateHistoryProvider.HistoryRateSnapshot> points = new ArrayList<>(size);
        LocalDate start = lastDate.minusDays(size - 1L);
        for (int i = 0; i < size; i++) {
            double amplitude = i >= size - 40 ? recentAmplitude : baseAmplitude;
            points.add(new FxRateHistoryProvider.HistoryRateSnapshot(
                    start.plusDays(i), BASE_RATE * Math.exp(amplitude * Math.sin(i * 1.7))));
        }
        return points;
    }
}
