package com.divurve.infra.macro;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.MacroIndicatorProvider;
import com.divurve.domain.port.MacroSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

/**
 * FRED(St. Louis Fed) API 로부터 거시지표의 최신 일별 관측값을 조회하는 어댑터 (이슈 #12).
 *
 * <p>NFR-DT-01/02/03: 실시간 시세 대신 종가/공표된 관측값만 사용하고, 출처(FRED)·기준일(asOf)을 함께 반환한다.
 * 캐시: {@code macro-latest} — 시리즈별 최근 관측값 캐싱.
 */
@ExternalAdapter
class FredMacroProvider implements MacroIndicatorProvider {

    private static final String SOURCE = "FRED";
    private static final String MISSING_VALUE = ".";

    private final RestClient restClient;
    private final FredProperties props;
    private final Clock clock;

    FredMacroProvider(RestClient externalRestClient, FredProperties props, Clock clock) {
        this.restClient = externalRestClient.mutate().baseUrl(props.baseUrl()).build();
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Cacheable(cacheNames = "macro-latest", key = "#root.args[0]")
    public MacroSnapshot fetchLatest(String seriesId) {
        String apiKey = Optional.ofNullable(props.apiKey())
            .filter(k -> !k.isBlank())
            .orElseThrow(() -> new IllegalStateException(
                "FRED API key is not configured (app.external.fred.api-key)"));

        FredResponse body = restClient.get()
            .uri(uri -> uri.path("/series/observations")
                .queryParam("series_id", seriesId)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json")
                .queryParam("sort_order", "desc")
                .queryParam("limit", 1)
                .build())
            .retrieve()
            .body(FredResponse.class);

        Observation obs = pickLatest(body, seriesId);
        return new MacroSnapshot(
            seriesId,
            new BigDecimal(obs.value()),
            LocalDate.parse(obs.date()),
            SOURCE,
            clock.instant()
        );
    }

    private Observation pickLatest(FredResponse body, String seriesId) {
        List<Observation> observations = Optional.ofNullable(body)
            .map(FredResponse::observations)
            .orElse(List.of());
        if (observations.isEmpty()) {
            throw new IllegalStateException("FRED returned no observations for series: " + seriesId);
        }
        return observations.stream()
            .filter(o -> o.value() != null && !MISSING_VALUE.equals(o.value()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "FRED returned only missing observations for series: " + seriesId));
    }

    record FredResponse(@JsonProperty("observations") List<Observation> observations) {
    }

    record Observation(
        @JsonProperty("date") String date,
        @JsonProperty("value") String value
    ) {
    }
}
