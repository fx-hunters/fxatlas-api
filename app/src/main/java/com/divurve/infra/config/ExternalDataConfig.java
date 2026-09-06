package com.divurve.infra.config;

import com.divurve.domain.forecast.ForecastService;
import com.divurve.domain.port.EconomicEventProvider;
import com.divurve.domain.port.FxRateHistoryProvider;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.MacroIndicatorProvider;
import com.divurve.infra.event.MockEconomicEventProvider;
import com.divurve.infra.fxrate.EcosFxRateHistoryProvider;
import com.divurve.infra.fxrate.EcosProperties;
import com.divurve.infra.macro.FredProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 데이터 인프라 설정 (이슈 #12, #16).
 *
 * <p>- {@link RestClient} 공용 빈: ECOS/FRED 어댑터가 각자 baseUrl 을 얹어 사용한다.
 * - Caffeine 기반 로컬 캐시: 일별 종가는 하루에 한 번만 갱신되므로 6h TTL 로 충분.
 * - 캐시 이름은 각 어댑터의 {@code @Cacheable} 애노테이션과 정확히 일치해야 한다.
 * - ForecastService: 팬차트, 전망 동인, 모델 성적표, 경제 일정 조회 UseCase
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties({EcosProperties.class, FredProperties.class})
public class ExternalDataConfig {

    static final Duration EXTERNAL_CACHE_TTL = Duration.ofHours(6);
    static final long EXTERNAL_CACHE_MAX_SIZE = 500;
    static final List<String> EXTERNAL_CACHE_NAMES = List.of("fx-latest", "macro-latest");

    @Bean
    RestClient externalRestClient() {
        return RestClient.builder().build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    CacheManager externalDataCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(EXTERNAL_CACHE_TTL)
            .maximumSize(EXTERNAL_CACHE_MAX_SIZE));
        manager.setCacheNames(EXTERNAL_CACHE_NAMES);
        return manager;
    }

    @Bean
    FxRateHistoryProvider fxRateHistoryProvider(RestClient externalRestClient, EcosProperties props) {
        // Spring이 classpath에서 자동으로 EcosFxRateHistoryProvider를 찾아 주입한다.
        // 여기서는 인터페이스 타입으로 메서드를 정의하고, @ExternalAdapter가 붙은 구현을 자동으로 주입받는다.
        return new com.divurve.infra.fxrate.EcosFxRateHistoryProvider(externalRestClient, props);
    }

    @Bean
    EconomicEventProvider economicEventProvider() {
        return new com.divurve.infra.event.MockEconomicEventProvider();
    }

    @Bean
    ForecastService forecastService(
        FxRateProvider fxRateProvider,
        FxRateHistoryProvider historyProvider,
        MacroIndicatorProvider macroProvider,
        EconomicEventProvider eventProvider
    ) {
        return new ForecastService(fxRateProvider, historyProvider, macroProvider, eventProvider);
    }
}
