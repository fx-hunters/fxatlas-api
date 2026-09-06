package com.divurve.infra.fxrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.github.benmanes.caffeine.cache.Caffeine;

class EcosFxRateProviderTest {

    private static final Instant NOW = Instant.parse("2026-03-05T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

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

    private EcosFxRateProvider provider() {
        return new EcosFxRateProvider(builder.build(), props, FIXED_CLOCK);
    }

    @Test
    void fetchLatest_returns_snapshot_with_source_and_asOf() {
        server.expect(method(HttpMethod.GET))
            .andExpect(requestTo(Matchers.containsString("/StatisticSearch/TEST_KEY/json/kr/1/15/731Y001/D/")))
            .andExpect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("""
                {"StatisticSearch":{"row":[
                  {"TIME":"20260302","DATA_VALUE":"1320.50"},
                  {"TIME":"20260304","DATA_VALUE":"1330.75"},
                  {"TIME":"20260303","DATA_VALUE":"1325.00"}
                ]}}
                """, MediaType.APPLICATION_JSON));

        RateSnapshot snap = provider().fetchLatest("USD_KRW");

        assertThat(snap.pairCode()).isEqualTo("USD_KRW");
        assertThat(snap.rate()).isEqualByComparingTo(new BigDecimal("1330.75"));
        assertThat(snap.asOf()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(snap.source()).isEqualTo("ECOS");
        assertThat(snap.fetchedAt()).isEqualTo(NOW);
        server.verify();
    }

    @Test
    void unsupported_pair_code_throws() {
        assertThatThrownBy(() -> provider().fetchLatest("XYZ_KRW"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("XYZ_KRW");
    }

    @Test
    void missing_api_key_throws_before_calling_ecos() {
        props = new EcosProperties("https://ecos.bok.or.kr/api", "", "731Y001", Map.of("USD_KRW", "0000001"));
        assertThatThrownBy(() -> provider().fetchLatest("USD_KRW"))
            .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void empty_rows_throws() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("{\"StatisticSearch\":{\"row\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().fetchLatest("USD_KRW"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no rows");
        server.verify();
    }

    @Test
    void empty_response_body_throws() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().fetchLatest("USD_KRW"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no rows");
        server.verify();
    }

    @Test
    void null_api_key_throws() {
        props = new EcosProperties("https://ecos.bok.or.kr/api", null, "731Y001", Map.of("USD_KRW", "0000001"));
        assertThatThrownBy(() -> provider().fetchLatest("USD_KRW"))
            .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void cache_hit_avoids_second_http_call() {
        server.expect(requestTo(Matchers.endsWith("/0000001")))
            .andRespond(withSuccess("""
                {"StatisticSearch":{"row":[{"TIME":"20260304","DATA_VALUE":"1330.75"}]}}
                """, MediaType.APPLICATION_JSON));

        CachingConfig.INSTANCE = new EcosFxRateProvider(builder.build(), props, FIXED_CLOCK);
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CachingConfig.class)) {
            FxRateProvider cached = ctx.getBean(FxRateProvider.class);
            RateSnapshot first = cached.fetchLatest("USD_KRW");
            RateSnapshot second = cached.fetchLatest("USD_KRW");
            assertThat(second).isEqualTo(first);
        }
        server.verify();
    }

    @Configuration
    @EnableCaching
    static class CachingConfig {
        static EcosFxRateProvider INSTANCE;

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager();
            manager.setCaffeine(Caffeine.newBuilder().maximumSize(10));
            manager.setCacheNames(java.util.List.of("fx-latest"));
            return manager;
        }

        @Bean
        EcosFxRateProvider provider() {
            return INSTANCE;
        }
    }
}
