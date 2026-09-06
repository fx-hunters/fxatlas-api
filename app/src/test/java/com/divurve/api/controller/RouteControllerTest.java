package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.api.dto.route.RouteContextResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.route.RouteContextService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RouteController} — {@code GET /api/v1/route/context} 는 직렬화만 한다(P).
 */
@DisplayName("RouteController")
class RouteControllerTest {

    private static final Instant FIXED = Instant.parse("2026-09-06T00:00:00Z");

    private final RouteController controller = new RouteController(
            new RouteContextService(Clock.fixed(FIXED, ZoneOffset.UTC)));

    @Test
    @DisplayName("RouteContext 를 data/meta 로 감싸 돌려주고 값은 비어 있다")
    void returnsEmptyContextWrappedInDataMeta() {
        ApiResponse<RouteContextResponse> response = controller.getRouteContext();

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().asOf()).isEqualTo(FIXED);
        assertThat(response.data().regime()).isNull();
        assertThat(response.data().diagnosis().status()).isNull();
        assertThat(response.data().diagnosis().grade()).isNull();
        assertThat(response.data().diagnosis().score()).isNull();
        assertThat(response.data().diagnosis().concentrationThreshold()).isNull();
        assertThat(response.data().portfolio().exposure()).isEmpty();
        assertThat(response.data().portfolio().totalAssetKrw()).isNull();
        assertThat(response.data().portfolio().fxAssetKrw()).isNull();
        assertThat(response.data().portfolio().fxRatio()).isNull();
        assertThat(response.data().forecast().pairCode()).isNull();
        assertThat(response.data().forecast().baseRate()).isNull();
        assertThat(response.data().forecast().vol30d()).isNull();
        assertThat(response.data().forecast().baseDate()).isNull();
        assertThat(response.data().forecast().interval80().lo()).isNull();
        assertThat(response.data().forecast().interval80().hi()).isNull();
        assertThat(response.data().stress().lastRunId()).isNull();
        assertThat(response.data().stress().totalEffectKrw()).isNull();
    }

    /** FR-FC-12 — 방향 전망을 Route 계산 입력으로 전달하지 않는다. 계약에서 잠근다. */
    @Test
    @DisplayName("응답 계약에 model_path·forecast_factors 가 없다 (FR-FC-12)")
    void responseContractExcludesDirectionalOutlook() {
        assertThat(RouteContextResponse.Forecast.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("pairCode", "baseRate", "interval80", "vol30d", "baseDate")
                .doesNotContain("modelPath", "forecastFactors");
    }
}
