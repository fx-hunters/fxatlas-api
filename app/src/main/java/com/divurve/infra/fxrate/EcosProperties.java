package com.divurve.infra.fxrate;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ECOS(한국은행 OpenAPI) 접속 설정.
 *
 * <p>NFR-DT-02: 환율의 1차 출처는 ECOS. API 키·통계 코드·통화쌍→ITEM_CODE 매핑을 외부 설정으로 관리한다.
 *
 * @param baseUrl    ECOS OpenAPI base URL (기본 https://ecos.bok.or.kr/api)
 * @param apiKey     발급 API 키 (환경변수 ECOS_API_KEY 로 주입)
 * @param statCode   환율 통계 코드 (기본 731Y001 = 원/미국달러 등 주요통화의 대원화환율)
 * @param itemCodes  통화쌍 코드 → ECOS ITEM_CODE 매핑 (예: USD_KRW → 0000001)
 */
@ConfigurationProperties(prefix = "app.external.ecos")
public record EcosProperties(
    String baseUrl,
    String apiKey,
    String statCode,
    Map<String, String> itemCodes
) {
    public EcosProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://ecos.bok.or.kr/api";
        }
        if (statCode == null || statCode.isBlank()) {
            statCode = "731Y001";
        }
        if (itemCodes == null) {
            itemCodes = Map.of();
        }
    }
}
