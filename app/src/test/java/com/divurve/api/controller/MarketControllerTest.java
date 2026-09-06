package com.divurve.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.market.MarketRegimeResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.market.MarketRegimeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketController")
class MarketControllerTest {

    @Test
    @DisplayName("배지·근거·안내를 그대로 옮기고 meta.regime 에 대표 국면을 싣는다")
    void getRegime() {
        MarketRegimeService service = mock(MarketRegimeService.class);
        when(service.getRegime()).thenReturn(view());

        ApiResponse<MarketRegimeResponse> response = new MarketController(service).getRegime();

        assertEquals("caution", response.data().badge());
        assertEquals("주의", response.data().badgeLabel());
        assertEquals("elevated", response.data().regime());
        assertEquals("elevated", response.meta().regime());
        // 예측 모델을 쓰지 않는 응답이라 model_version 은 싣지 않는다.
        assertNull(response.meta().modelVersion());
        assertEquals(0.72, response.data().pairRegimes().get("USDKRW").volPercentile5y());
        assertEquals(1, response.data().checks().size());
        assertEquals("vol_percentile", response.data().checks().get(0).key());
        // FR-SF-01 — 어떤 상태에서도 true 다.
        assertTrue(response.data().guidance().keepServingForecast());
        assertTrue(response.data().anomaly().note().contains("실제 시장 충격"));
    }

    private static MarketRegimeService.MarketRegimeView view() {
        Map<String, MarketRegimeService.PairRegimeView> pairs = new LinkedHashMap<>();
        pairs.put("USDKRW", new MarketRegimeService.PairRegimeView("elevated", 0.061, 0.72));

        return new MarketRegimeService.MarketRegimeView(
                "caution",
                "주의",
                "elevated",
                pairs,
                List.of(new MarketRegimeService.CheckView(
                        "vol_percentile", false, "USDKRW 30일 변동성이 5년 상위 28% 구간입니다.")),
                new MarketRegimeService.GuidanceView(true, true, true),
                new MarketRegimeService.AnomalyView(false, MarketRegimeService.ANOMALY_NOTE));
    }
}
