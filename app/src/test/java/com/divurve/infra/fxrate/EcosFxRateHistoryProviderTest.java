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

    // ── ECOS 오류 응답 (이슈 #57) ─────────────────────────────────────────────
    // ECOS 는 실패도 HTTP 200 으로 돌려주고 본문에 RESULT 블록만 담는다.
    // 이것을 보지 않으면 인증키 오류가 "행이 없다"로 뭉개져 라이브 전환 실패를 놓친다.

    @Test
    void 인증키_오류는_빈_결과가_아니라_예외다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"CODE":"INFO-100","MESSAGE":"인증키가 유효하지 않습니다."}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("INFO-100");
    }

    @Test
    void 서버_오류_코드도_예외로_세운다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"CODE":"ERROR-602","MESSAGE":"과도한 트래픽입니다."}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ERROR-602");
    }

    @Test
    void 예외_메시지에_ECOS_원문이나_API_키가_실리지_않는다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"CODE":"INFO-100","MESSAGE":"인증키가 유효하지 않습니다."}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .hasMessageNotContaining("TEST_KEY")
            .hasMessageNotContaining("인증키");
    }

    @Test
    void 코드가_없는_RESULT_는_오류로_보지_않는다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"MESSAGE":"설명만 있는 응답"}}
            """, MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
    }

    @Test
    void 본문이_비어_있으면_빈_결과다() {
        server.expect(method(HttpMethod.GET))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
    }

    @Test
    void 자료_없음_코드는_오류가_아니라_빈_결과다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
            """, MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5)).isEmpty();
    }

    @Test
    void 정상_코드가_함께_와도_행을_그대로_읽는다() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
            """
            {"RESULT":{"CODE":"INFO-000","MESSAGE":"정상"},
             "StatisticSearch":{"row":[{"TIME":"20260305","DATA_VALUE":"1330.00"}]}}
            """, MediaType.APPLICATION_JSON));

        assertThat(provider().fetchHistorical("USD_KRW", END_DATE, 5))
            .containsExactly(new HistoryRateSnapshot(LocalDate.of(2026, 3, 5), 1330.00));
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
            .hasMessageContaining("lookbackCalendarDays must be positive");
        assertThatThrownBy(() -> provider().fetchHistorical("USD_KRW", END_DATE, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lookbackCalendarDays must be positive");
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
