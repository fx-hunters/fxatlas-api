package com.divurve.infra.macro;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FRED(St. Louis Fed) API 접속 설정.
 *
 * <p>NFR-DT-02: 거시지표의 1차 출처는 FRED.
 *
 * @param baseUrl FRED API base URL (기본 https://api.stlouisfed.org/fred)
 * @param apiKey  발급 API 키 (환경변수 FRED_API_KEY 로 주입)
 */
@ConfigurationProperties(prefix = "app.external.fred")
public record FredProperties(String baseUrl, String apiKey) {
    public FredProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.stlouisfed.org/fred";
        }
    }
}
