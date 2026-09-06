package com.divurve.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RouteContextService} · {@link RouteContext} — <b>계약만</b> 검증한다.
 *
 * <p>이 단계에서 검증할 것은 "무엇을 계산했는가"가 아니라 <b>아무것도 계산하지 않는다</b>는 사실과
 * 필드 구조다. Route 계산 규칙이 요구사항 v2 §4.12 에서 전부 미확정이므로, 값이 채워져 있으면
 * 그 자체가 회귀다.
 */
@DisplayName("RouteContextService")
class RouteContextServiceTest {

    private static final Instant FIXED = Instant.parse("2026-09-06T00:00:00Z");

    private final RouteContextService service =
            new RouteContextService(Clock.fixed(FIXED, ZoneOffset.UTC));

    @Test
    @DisplayName("기준 시각만 실제 값이고 나머지는 전부 비어 있다")
    void contextIsEmptyExceptAsOf() {
        RouteContext context = service.getContext();

        assertThat(context.asOf()).isEqualTo(FIXED);
        assertThat(context.regime()).isNull();

        assertThat(context.diagnosis().status()).isNull();
        assertThat(context.diagnosis().grade()).isNull();
        assertThat(context.diagnosis().score()).isNull();
        assertThat(context.diagnosis().concentrationThreshold()).isNull();

        assertThat(context.portfolio().totalAssetKrw()).isNull();
        assertThat(context.portfolio().fxAssetKrw()).isNull();
        assertThat(context.portfolio().fxRatio()).isNull();
        assertThat(context.portfolio().exposure()).isEmpty();

        assertThat(context.forecast().pairCode()).isNull();
        assertThat(context.forecast().baseRate()).isNull();
        assertThat(context.forecast().vol30d()).isNull();
        assertThat(context.forecast().baseDate()).isNull();
        assertThat(context.forecast().interval80().lo()).isNull();
        assertThat(context.forecast().interval80().hi()).isNull();

        assertThat(context.stress().lastRunId()).isNull();
        assertThat(context.stress().totalEffectKrw()).isNull();
    }

    @Test
    @DisplayName("노출 맵은 null 이면 빈 맵으로, 값이 있으면 방어적 복사본으로 담긴다")
    void exposureIsNullSafeAndDefensivelyCopied() {
        assertThat(new RouteContext.Portfolio(null, null, null, null).exposure()).isEmpty();

        RouteContext.Portfolio portfolio =
                new RouteContext.Portfolio(1_000L, 400L, 0.4, Map.of("USD", 0.4));

        assertThat(portfolio.exposure()).containsExactly(Map.entry("USD", 0.4));
        assertThat(portfolio.totalAssetKrw()).isEqualTo(1_000L);
        assertThat(portfolio.fxAssetKrw()).isEqualTo(400L);
        assertThat(portfolio.fxRatio()).isEqualTo(0.4);
    }

    /**
     * FR-FC-12 회귀 방지 — 방향 전망(모델 경로·요인 분해)은 Route 계산 입력이 될 수 없다.
     * 계약에 필드가 존재하지 않는다는 것을 리플렉션으로 못박는다.
     */
    @Test
    @DisplayName("model_path·forecast_factors 는 계약에 존재하지 않는다 (FR-FC-12)")
    void forecastContractExcludesDirectionalOutlook() {
        assertThat(RouteContext.Forecast.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("pairCode", "baseRate", "interval80", "vol30d", "baseDate")
                .doesNotContain("modelPath", "forecastFactors");
    }

    @Test
    @DisplayName("값이 채워진 경우에도 그대로 전달만 한다 — 변환·계산이 없다")
    void carriesValuesUnchanged() {
        RouteContext.Diagnosis diagnosis =
                new RouteContext.Diagnosis("detailed_done", "balanced", 55, 0.3);
        RouteContext.Forecast forecast = new RouteContext.Forecast(
                "USD_KRW",
                1350.0,
                new RouteContext.Forecast.Interval(1300.0, 1400.0),
                0.08,
                java.time.LocalDate.of(2026, 9, 6));
        RouteContext.Stress stress = new RouteContext.Stress("run-1", -120_000L);
        RouteContext context = new RouteContext(
                FIXED, diagnosis, RouteContext.Portfolio.empty(), forecast, stress, "normal");

        assertThat(context.diagnosis()).isSameAs(diagnosis);
        assertThat(context.diagnosis().status()).isEqualTo("detailed_done");
        assertThat(context.diagnosis().grade()).isEqualTo("balanced");
        assertThat(context.diagnosis().score()).isEqualTo(55);
        assertThat(context.diagnosis().concentrationThreshold()).isEqualTo(0.3);
        assertThat(context.forecast().pairCode()).isEqualTo("USD_KRW");
        assertThat(context.forecast().baseRate()).isEqualTo(1350.0);
        assertThat(context.forecast().interval80().lo()).isEqualTo(1300.0);
        assertThat(context.forecast().interval80().hi()).isEqualTo(1400.0);
        assertThat(context.forecast().vol30d()).isEqualTo(0.08);
        assertThat(context.forecast().baseDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 6));
        assertThat(context.stress().lastRunId()).isEqualTo("run-1");
        assertThat(context.stress().totalEffectKrw()).isEqualTo(-120_000L);
        assertThat(context.regime()).isEqualTo("normal");
    }
}
