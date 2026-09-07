package com.divurve.infra.fxrate;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.FxRateHistoryProvider;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

/**
 * ECOS(한국은행) OpenAPI로부터 환율 히스토리를 조회하는 어댑터.
 *
 * <p>FxRateProvider와 다르게 과거 환율들을 일괄 조회한다.
 * 팬차트 생성을 위한 변동성 계산, 모델 성적표 생성에 필요하다.
 *
 * <p>NFR-DT-01/02: 어댑터는 수치를 생성하지 않고 ECOS의 일별 종가를 그대로 전달한다.
 */
@ExternalAdapter
public class EcosFxRateHistoryProvider implements FxRateHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(EcosFxRateHistoryProvider.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final EcosProperties props;

    public EcosFxRateHistoryProvider(RestClient externalRestClient, EcosProperties props) {
        this.restClient = restClient(externalRestClient, props);
        this.props = Objects.requireNonNull(props);
    }

    private static RestClient restClient(RestClient shared, EcosProperties props) {
        return shared.mutate().baseUrl(props.baseUrl()).build();
    }

    @Override
    @Cacheable(cacheNames = "fx-history",
        key = "{#root.args[0], #root.args[1], #root.args[2]}")
    public List<HistoryRateSnapshot> fetchHistorical(String pairCode, LocalDate endDate, int lookbackCalendarDays) {
        Objects.requireNonNull(pairCode, "pairCode must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (lookbackCalendarDays <= 0) {
            throw new IllegalArgumentException("lookbackCalendarDays must be positive");
        }

        String itemCode = props.itemCodes().get(pairCode);
        if (itemCode == null) {
            throw new IllegalArgumentException("Unsupported pairCode for ECOS: " + pairCode);
        }

        String apiKey = Optional.ofNullable(props.apiKey())
            .filter(k -> !k.isBlank())
            .orElseThrow(() -> new IllegalStateException(
                "ECOS API key is not configured (app.external.ecos.api-key)"));

        LocalDate startDate = endDate.minusDays(lookbackCalendarDays);
        String start = startDate.format(YYYYMMDD);
        String end = endDate.format(YYYYMMDD);

        String path = "/StatisticSearch/%s/json/kr/1/%d/%s/D/%s/%s/%s".formatted(
            apiKey, lookbackCalendarDays + 1, props.statCode(), start, end, itemCode
        );

        EcosResponse body = restClient.get().uri(path).retrieve().body(EcosResponse.class);
        List<EcosRow> rows = extractRows(body, pairCode);

        List<HistoryRateSnapshot> snapshots = new ArrayList<>();
        for (EcosRow row : rows) {
            LocalDate date = LocalDate.parse(row.time(), YYYYMMDD);
            Double rate = Double.parseDouble(row.dataValue());
            snapshots.add(new HistoryRateSnapshot(date, rate));
        }

        return snapshots;
    }

    /**
     * 응답에서 행을 꺼낸다. ECOS 는 실패도 HTTP 200 으로 돌려주므로 {@code RESULT} 블록을 먼저 본다
     * (이슈 #57). {@code INFO-200}(자료 없음)만 빈 결과로 넘기고, 나머지 오류는 예외로 세운다 —
     * 인증키 오류가 "데이터 없음"으로 둔갑하면 라이브 전환 실패를 아무도 눈치채지 못한다.
     */
    private List<EcosRow> extractRows(EcosResponse body, String pairCode) {
        EcosResult result = body == null ? null : body.result();
        if (result != null && !result.isBenign()) {
            // MESSAGE 는 로그에만 남긴다 — 응답으로 외부 시스템 문구를 그대로 내보내지 않는다.
            log.warn("ECOS 오류 응답 code={} message={} pair={}",
                result.code(), result.message(), pairCode);
            throw new IllegalStateException(
                "ECOS request failed with result code " + result.code());
        }
        if (result != null && result.isNoData()) {
            return List.of();
        }
        return Optional.ofNullable(body)
            .map(EcosResponse::statisticSearch)
            .map(StatisticSearch::row)
            .orElse(List.of());
    }

    // ── 응답 매핑 ────────────────────────────────────

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
