package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.xray.AttributionResponse;
import com.divurve.api.dto.xray.StressRequest;
import com.divurve.api.dto.xray.StressResponse;
import com.divurve.api.dto.xray.XrayResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.xray.XrayService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link XrayController} — 도메인 경계 record → 응답 DTO 변환, data/meta 래핑 검증.
 * 컨트롤러는 engine 타입을 직접 만지지 않고 {@link XrayService} 가 준 값만 옮긴다.
 */
@ExtendWith(MockitoExtension.class)
class XrayControllerTest {

    @Mock
    private XrayService xrayService;

    private final UUID userId = UUID.randomUUID();

    private XrayController controller() {
        return new XrayController(xrayService);
    }

    @Test
    void getXray_는_통화별_노출과_1퍼센트_민감도를_계산해_data_meta_로_감싼다() {
        Map<String, Long> assets = new LinkedHashMap<>();
        assets.put("USD", 3_250_000L);
        assets.put("JPY", 45_000L);
        Map<String, Double> exposure = new LinkedHashMap<>();
        exposure.put("USD", 0.6922);
        // JPY 비중이 빠져 있으면 0.0 으로 채운다.
        when(xrayService.getPortfolio(userId)).thenReturn(new XrayService.PortfolioSnapshot(
                userId, 4_695_000L, 4_695_000L, 1.0, assets, exposure, "USD", 0.6922, 0.35, "warning"));

        ApiResponse<XrayResponse> response = controller().getXray(userId);

        assertThat(response.meta()).isNotNull();
        XrayResponse data = response.data();
        assertThat(data.totalAssetKrw()).isEqualTo(4_695_000L);
        assertThat(data.fxAssetKrw()).isEqualTo(4_695_000L);
        assertThat(data.fxRatio()).isEqualTo(1.0);
        assertThat(data.exposure()).containsExactly(
                new XrayResponse.Exposure("USD", 3_250_000L, 0.6922),
                new XrayResponse.Exposure("JPY", 45_000L, 0.0));
        assertThat(data.concentration())
                .isEqualTo(new XrayResponse.Concentration("USD", 0.6922, 0.35, "warning"));
        // 1% 민감도: USD 32,500 + JPY 450 = 32,950
        assertThat(data.sensitivity1pct().totalKrw()).isEqualTo(32_950L);
        assertThat(data.sensitivity1pct().byCurrency())
                .containsOnly(entry("USD", 32_500L), entry("JPY", 450L));
        assertThat(data.dayChangeKrw()).isZero();
        assertThat(data.upcomingOutflows()).isEmpty();
    }

    @Test
    void getAttribution_은_three_way_이면_교차항까지_네_구성요소를_담는다() {
        when(xrayService.getAttribution(userId, "USD", "three_way")).thenReturn(analysis("three_way"));

        ApiResponse<AttributionResponse> response = controller().getAttribution(userId, "USD", "three_way");

        AttributionResponse data = response.data();
        assertThat(response.meta()).isNotNull();
        assertThat(data.currencyCode()).isEqualTo("USD");
        assertThat(data.mode()).isEqualTo("three_way");
        assertThat(data.costBasisKrw()).isEqualTo(1_300_000L);
        assertThat(data.currentKrw()).isEqualTo(1_430_000L);
        assertThat(data.totalReturn()).isEqualTo(0.1);
        // 비율은 퍼센트 포인트로 변환된다 (× 100).
        assertThat(data.components()).containsExactly(
                new AttributionResponse.Component("asset", 50_000L, 4.0),
                new AttributionResponse.Component("fx", 90_000L, 6.0),
                new AttributionResponse.Component("cost", -10_000L, -1.0),
                new AttributionResponse.Component("interaction", 5_000L, 0.5));
        assertThat(data.byHolding()).isEmpty();
    }

    @Test
    void getAttribution_은_shapley_이면_교차항을_따로_내보내지_않고_통화가_없으면_ALL_로_표기한다() {
        when(xrayService.getAttribution(userId, null, "shapley")).thenReturn(analysis("shapley"));

        AttributionResponse data = controller().getAttribution(userId, null, "shapley").data();

        assertThat(data.currencyCode()).isEqualTo("ALL");
        assertThat(data.mode()).isEqualTo("shapley");
        assertThat(data.components()).extracting(AttributionResponse.Component::key)
                .containsExactly("asset", "fx", "cost");
    }

    @Test
    void applyStress_는_통화별_영향도를_리스트로_변환한다() {
        Map<String, XrayService.CurrencyStressImpactData> byCurrency = new LinkedHashMap<>();
        byCurrency.put("USD", new XrayService.CurrencyStressImpactData("USD", 0.1, 130_000L));
        byCurrency.put("JPY", new XrayService.CurrencyStressImpactData("JPY", -0.05, -9_000L));
        StressRequest request = new StressRequest(Map.of("USD", 0.1, "JPY", -0.05));
        when(xrayService.applyStress(userId, request.shocks())).thenReturn(new XrayService.StressAnalysis(
                1_480_000L, 1_601_000L, 121_000L, 0.08175675675675675, byCurrency));

        ApiResponse<StressResponse> response = controller().applyStress(userId, request);

        assertThat(response.meta()).isNotNull();
        StressResponse data = response.data();
        assertThat(data.totalAssetBeforeKrw()).isEqualTo(1_480_000L);
        assertThat(data.totalAssetAfterKrw()).isEqualTo(1_601_000L);
        assertThat(data.impactKrw()).isEqualTo(121_000L);
        assertThat(data.impactRatio()).isEqualTo(0.08175675675675675);
        assertThat(data.byCurrency()).containsExactly(
                new StressResponse.ByCurrency("USD", 0.1, 130_000L),
                new StressResponse.ByCurrency("JPY", -0.05, -9_000L));
    }

    private static XrayService.AttributionAnalysis analysis(String mode) {
        return new XrayService.AttributionAnalysis(
                1_300_000L,
                1_430_000L,
                0.1,
                new XrayService.AttributionComponentData("asset", 0.04, 50_000L),
                new XrayService.AttributionComponentData("fx", 0.06, 90_000L),
                new XrayService.AttributionComponentData("interaction", 0.005, 5_000L),
                new XrayService.AttributionComponentData("cost", -0.01, -10_000L),
                mode);
    }
}
