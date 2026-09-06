package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.fit.ConcentrationResponse;
import com.divurve.api.dto.fit.SimulateRequest;
import com.divurve.api.dto.fit.SimulateResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.fit.FitService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FitController} — 진단 결과 → 응답 DTO 변환, 상태별 제안 문구, 임계값 판정 검증.
 */
@ExtendWith(MockitoExtension.class)
class FitControllerTest {

    @Mock
    private FitService fitService;

    private final UUID userId = UUID.randomUUID();

    private FitController controller() {
        return new FitController(fitService);
    }

    @Test
    void getConcentration_은_경고_상태이면_분산_제안_두_건을_붙인다() {
        Map<String, Double> exposure = new LinkedHashMap<>();
        exposure.put("USD", 0.6922);
        exposure.put("JPY", 0.3078);
        when(fitService.diagnoseConcentration(userId)).thenReturn(new FitService.ConcentrationDiagnosis(
                exposure, "USD", 0.6922, 0.35, "warning", userId));

        ApiResponse<ConcentrationResponse> response = controller().getConcentration(userId);

        assertThat(response.meta()).isNotNull();
        ConcentrationResponse data = response.data();
        assertThat(data.exposure()).containsOnly(entry("USD", 0.6922), entry("JPY", 0.3078));
        assertThat(data.topCurrency()).isEqualTo("USD");
        assertThat(data.topShare()).isEqualTo(0.6922);
        assertThat(data.threshold()).isEqualTo(0.35);
        assertThat(data.status()).isEqualTo("warning");
        assertThat(data.suggestions()).containsExactly(
                "USD 비중이 높습니다. 분산 투자를 고려하세요.",
                "다른 통화에 적립하여 위험을 분산하세요.");
    }

    @Test
    void getConcentration_은_안전_상태이면_균형_문구_한_건만_붙인다() {
        when(fitService.diagnoseConcentration(userId)).thenReturn(new FitService.ConcentrationDiagnosis(
                Map.of("USD", 0.25), "USD", 0.25, 0.35, "safe", userId));

        ConcentrationResponse data = controller().getConcentration(userId).data();

        assertThat(data.status()).isEqualTo("safe");
        assertThat(data.suggestions()).containsExactly("포트폴리오 균형이 적절합니다.");
    }

    @Test
    void simulate_는_조정_후_최대_비중이_임계값_이내면_within_threshold_를_참으로_준다() {
        Map<String, Double> adjusted = new LinkedHashMap<>();
        adjusted.put("USD", 0.3416);
        adjusted.put("EUR", 0.6584);
        SimulateRequest request = new SimulateRequest("USD", 0.1);
        when(fitService.simulateDiversification(userId, "USD", 0.1)).thenReturn(
                new FitService.DiversificationSimulation(0.0907, 0.0918, adjusted, 0.35, 0.3416, userId, "USD"));

        ApiResponse<SimulateResponse> response = controller().simulate(userId, request);

        assertThat(response.meta()).isNotNull();
        SimulateResponse data = response.data();
        assertThat(data.portfolioVol()).isEqualTo(new SimulateResponse.PortfolioVol(0.0907, 0.0918));
        assertThat(data.exposureAfter()).containsExactly(entry("USD", 0.3416), entry("EUR", 0.6584));
        assertThat(data.threshold()).isEqualTo(0.35);
        assertThat(data.withinThreshold()).isTrue();
        assertThat(data.suggestedGoal())
                .isEqualTo(new SimulateResponse.SuggestedGoal("saving", "diversification", "USD", 0.0));
    }

    @Test
    void simulate_는_조정_후에도_임계값을_넘으면_within_threshold_를_거짓으로_준다() {
        SimulateRequest request = new SimulateRequest("EUR", -0.05);
        when(fitService.simulateDiversification(userId, "EUR", -0.05)).thenReturn(
                new FitService.DiversificationSimulation(
                        0.11, 0.10, Map.of("EUR", 0.62, "USD", 0.38), 0.35, 0.62, userId, "EUR"));

        SimulateResponse data = controller().simulate(userId, request).data();

        assertThat(data.withinThreshold()).isFalse();
        assertThat(data.suggestedGoal().currencyCode()).isEqualTo("EUR");
        assertThat(data.exposureAfter()).containsOnly(entry("EUR", 0.62), entry("USD", 0.38));
    }
}
