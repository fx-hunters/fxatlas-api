package com.divurve.infra.fxrate;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

/**
 * ECOS(한국은행) OpenAPI 로부터 환율 일별 종가를 조회하는 어댑터 (이슈 #12).
 *
 * <p>NFR-DT-01/02: 어댑터는 수치를 생성하지 않고 출처(ECOS)의 일별 종가를 그대로 전달한다.
 * NFR-DT-03: 실시간 시세 대신 최근 영업일 종가만 사용한다.
 * 캐시: {@code fx-latest} — 통화쌍별 최근 종가 캐싱(일 단위 만료는 CacheConfig 에서 관리).
 */
@ExternalAdapter
class EcosFxRateProvider implements FxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(EcosFxRateProvider.class);

    private static final String SOURCE = "ECOS";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LOOKBACK_DAYS = 14;

    private final RestClient restClient;
    private final EcosProperties props;
    private final Clock clock;

    EcosFxRateProvider(RestClient externalRestClient, EcosProperties props, Clock clock) {
        this.restClient = restClient(externalRestClient, props);
        this.props = props;
        this.clock = clock;
    }

    private static RestClient restClient(RestClient shared, EcosProperties props) {
        return shared.mutate().baseUrl(props.baseUrl()).build();
    }

    @Override
    @Cacheable(cacheNames = "fx-latest", key = "#root.args[0]")
    public RateSnapshot fetchLatest(String pairCode) {
        String itemCode = props.itemCodes().get(pairCode);
        if (itemCode == null) {
            throw new IllegalArgumentException("Unsupported pairCode for ECOS: " + pairCode);
        }
        String apiKey = Optional.ofNullable(props.apiKey())
            .filter(k -> !k.isBlank())
            .orElseThrow(() -> new IllegalStateException(
                "ECOS API key is not configured (app.external.ecos.api-key)"));

        // 주입되는 Clock 은 이미 KST 다(ExternalDataConfig#systemClock, 이슈 #99). 그래도 withZone 을
        // 남겨 둔다 — ECOS 는 KST 영업일로 고시하므로, Clock 이 어떤 타임존이든 여기서 보는 "오늘"은
        // KST 여야 한다. 어댑터가 외부 규약을 스스로 지키게 하는 편이 안전하다.
        LocalDate today = LocalDate.now(clock.withZone(KST));
        String end = today.format(YYYYMMDD);
        String start = today.minusDays(LOOKBACK_DAYS).format(YYYYMMDD);

        String path = "/StatisticSearch/%s/json/kr/1/%d/%s/D/%s/%s/%s".formatted(
            apiKey, LOOKBACK_DAYS + 1, props.statCode(), start, end, itemCode
        );

        EcosResponse body = restClient.get().uri(path).retrieve().body(EcosResponse.class);
        EcosRow latest = pickLatest(body, pairCode);
        return new RateSnapshot(
            pairCode,
            new BigDecimal(latest.dataValue()),
            LocalDate.parse(latest.time(), YYYYMMDD),
            SOURCE,
            clock.instant()
        );
    }

    /**
     * 최근 종가 행을 고른다. ECOS 는 실패도 HTTP 200 으로 돌려주므로 {@code RESULT} 블록을 먼저 본다
     * (이슈 #57) — 그러지 않으면 인증키 오류가 "행이 없다"로 뭉개진다.
     */
    private EcosRow pickLatest(EcosResponse body, String pairCode) {
        EcosResult result = body == null ? null : body.result();
        if (result != null && !result.isBenign()) {
            // MESSAGE 는 로그에만 남긴다 — 응답으로 외부 시스템 문구를 그대로 내보내지 않는다.
            log.warn("ECOS 오류 응답 code={} message={} pair={}",
                result.code(), result.message(), pairCode);
            throw new IllegalStateException(
                "ECOS request failed with result code " + result.code());
        }
        List<EcosRow> rows = Optional.ofNullable(body)
            .map(EcosResponse::statisticSearch)
            .map(StatisticSearch::row)
            .orElse(List.of());
        if (rows.isEmpty()) {
            throw new IllegalStateException("ECOS returned no rows for the requested window");
        }
        return rows.stream()
            .max((a, b) -> a.time().compareTo(b.time()))
            .orElseThrow();
    }

    // ── 응답 매핑 (ECOS 원본 필드명이 UpperSnake 라서 @JsonProperty 대신 record + Jackson 매핑용 별도 컨버터 없이
    // 필드명을 소문자로 두고 Jackson 이 case-insensitive 하게 매핑하도록 아래 구조를 사용한다) ───────
    record EcosResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("StatisticSearch") StatisticSearch statisticSearch,
        @com.fasterxml.jackson.annotation.JsonProperty("RESULT") EcosResult result
    ) {
    }

    record StatisticSearch(@com.fasterxml.jackson.annotation.JsonProperty("row") List<EcosRow> row) {
    }

    record EcosRow(
        @com.fasterxml.jackson.annotation.JsonProperty("TIME") String time,
        @com.fasterxml.jackson.annotation.JsonProperty("DATA_VALUE") String dataValue
    ) {
    }

}
