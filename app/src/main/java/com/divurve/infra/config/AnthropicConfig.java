package com.divurve.infra.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.divurve.infra.ai.AnthropicMessageClient;
import com.divurve.infra.ai.AnthropicProperties;
import com.divurve.infra.ai.ClaudeMessageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic SDK 배선 (이슈 #73). {@code app.external.anthropic.enabled=true} 일 때만 활성화된다.
 *
 * <p>{@link ExternalDataConfig} 와 달리 별도 설정 클래스로 둔다 — ECOS/FRED 는 수치 출처이고
 * Claude 는 서술 경로라서 켜고 끄는 조건이 다르다.
 */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
@ConditionalOnProperty(prefix = "app.external.anthropic", name = "enabled", havingValue = "true")
public class AnthropicConfig {

    /**
     * SDK 자체 재시도는 끈다 (이슈 #73 확정). SDK 기본값 2 를 그대로 두면 {@code AiService} 의
     * 도메인 재시도(최대 2회)와 곱해져 한 번의 화면 조회가 최악 6회 호출이 된다.
     * 재시도 정책은 도메인 한 곳에서만 정한다.
     */
    static final int SDK_MAX_RETRIES = 0;

    @Bean
    AnthropicClient anthropicClient(AnthropicProperties props) {
        return AnthropicOkHttpClient.builder()
            .apiKey(props.requireApiKey())
            .maxRetries(SDK_MAX_RETRIES)
            .timeout(props.requestTimeout())
            .build();
    }

    @Bean
    ClaudeMessageClient claudeMessageClient(AnthropicClient anthropicClient, AnthropicProperties props) {
        return new AnthropicMessageClient(anthropicClient, props);
    }
}
