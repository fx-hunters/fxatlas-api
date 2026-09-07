package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.api.dto.route.RouteContextResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.route.RouteContext;
import com.divurve.domain.route.RouteContextService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link RouteController} — {@code GET /api/v1/route/context} 는 직렬화만 한다.
 *
 * <p>컨트롤러가 값을 바꾸지 않는다는 것과, 채우지 못한 블록이 비어서 내려간다는 것을 본다.
 * 값을 실제로 모으는 책임은 {@code RouteContextService} 에 있고 그쪽에서 따로 검증한다.
 */
@DisplayName("RouteController")
class RouteControllerTest {

    private static final Instant FIXED = Instant.parse("2026-09-06T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final RouteContextService routeContextService = Mockito.mock(RouteContextService.class);
    private final RouteController controller = new RouteController(routeContextService);

    @Test
    @DisplayName("채우지 못한 블록은 비어서 내려간다 — 값을 지어내지 않는다")
    void returnsEmptyContextWrappedInDataMeta() {
        Mockito.when(routeContextService.getContext(USER_ID)).thenReturn(RouteContext.empty(FIXED));

        ApiResponse<RouteContextResponse> response = controller.getRouteContext(USER_ID);

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
