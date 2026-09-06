package com.divurve.infra.fxrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.divurve.domain.port.FxRateHistoryProvider.HistoryRateSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link EcosFxRateHistoryProvider} 단위 테스트.
 *
 * <p>실제 네트워크를 타지 않도록 {@link MockRestServiceServer} 로 ECOS 응답을 고정한다.
 * 어댑터는 수치를 만들지 않고 ECOS 일별 종가를 그대로 전달해야 한다(NFR-DT-01/02).
 */
class EcosFxRateHistoryProviderTest {

    private static final LocalDate END_DATE = LocalDate.of(2026, 3, 5);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private EcosProperties props;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        props = new EcosProperties(
            "https://ecos.bok.or.kr/api",
            "TEST_KEY",
            "731Y001",
            Map.of("USD_KRW", "0000001")
        );
    }

    private EcosFxRateHistoryProvider provider() {
        return new EcosFxRateHistoryProvider(builder.build(), props);
    }

    @Test
    void fetchHistorical_maps_every_row_in_response_order() {
        // days=5 → 조회 개수 days+1=6, 시작일 = endDate - 5일
        server.expect(method(HttpMethod.GET))
            .andExpect(requestTo(Matchers.containsString(
                "/StatisticSearch/TEST_KEY/json/kr/1/6/731Y001/D/20260228/20260305/0000001")))
            .andRespond(withSuccess("""
                {"StatisticSearch":{"row":[
                  {"TIME":"20260302","DATA_VALUE":"1320.50"},
                  {"TIME":"20260303","DATA_VALUE":"1325.00"},
                  {"TIME":"20260304","DATA_VALUE":"1330.75"}
                ]}}
                """, MediaType.APPLICATION_JSON));

        List<HistoryRateSnapshot> snapshots = provider().fetchHistorical("USD_KRW", END_DATE, 5);

        assertThat(snapshots).containsExactly(
            new HistoryRateSnapshot(LocalDate.of(2026, 3, 2), 1320.50),
            new HistoryRateSnapshot(LocalDate.of(2026, 3, 3), 1325.00),
            new HistoryRateSnapshot(LocalDate.of(2026, 3, 4), 1330.75)
        );
        server.verify();
    }

    @Test
    void fetchHistorical_returns_empty_list_when_rows_are_empty() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("{\"StatisticSearch\":{\"row\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
        server.verify();
    }

    @Test
    void fetchHistorical_returns_empty_list_when_body_has_no_statistic_search() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
        server.verify();
    }

    @Test
    void fetchHistorical_returns_empty_list_when_statistic_search_has_no_rows() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("{\"StatisticSearch\":{}}", MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
        server.verify();
    }

    @Test
    void null_pair_code_throws() {
        assertThatThrownBy(() -> provider().fetchHistorical(null, END_DATE, 5))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("pairCode");
        server.verify();
    }

    @Test
    void null_end_date_throws() {
        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", null, 5))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("endDate");
        server.verify();
    }

    @Test
    void non_positive_days_throws() {
        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("days must be positive");
        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("days must be positive");
        server.verify();
    }

    @Test
    void unsupported_pair_code_throws() {
        assertThatThrownBy(() -> provider().fetchHistorical("XYZ_KRW", END_DATE, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("XYZ_KRW");
        server.verify();
    }

    @Test
    void blank_api_key_throws_before_calling_ecos() {
        props = new EcosProperties("https://ecos.bok.or.kr/api", "  ", "731Y001", Map.of("USD_KRW", "0000001"));

        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ECOS API key is not configured");
        server.verify();
    }

    @Test
    void null_api_key_throws_before_calling_ecos() {
        props = new EcosProperties("https://ecos.bok.or.kr/api", null, "731Y001", Map.of("USD_KRW", "0000001"));

        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ECOS API key is not configured");
        server.verify();
    }

    @Test
    void null_props_throws() {
        assertThatThrownBy(() -> new EcosFxRateHistoryProvider(builder.build(), null))
            .isInstanceOf(NullPointerException.class);
    }
}
