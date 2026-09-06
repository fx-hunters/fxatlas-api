package com.divurve.infra.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic Claude API 접속 설정 (이슈 #73).
 *
 * <p>기본값은 이슈 #73 "확정 사항" 표를 그대로 옮긴 것이다. 특히 <b>타임아웃 5초</b>는 AI 서술이
 * 동기 HTTP 요청 안에서 일어나기 때문이다 — SDK 기본값(10분)을 그대로 두면 Anthropic 지연이 그대로
 * 우리 응답 지연이 되어 NFR-AI-03(AI 실패가 서비스 실패가 되지 않는다)을 지킬 수 없다.
 *
 * <p>SDK 자체 재시도는 {@code AnthropicConfig} 에서 <b>0</b> 으로 고정한다. 기본값 2 를 두면
 * {@code AiService} 의 도메인 재시도(최대 2회)와 곱해져 최악 6회 호출이 된다 — 과금도 지연도 6배다.
 *
 * @param enabled        실 API 사용 여부. {@code false}(기본)면 {@code MockAiProvider} 가 그대로 쓰인다
 * @param apiKey         발급 API 키 (환경변수 {@code ANTHROPIC_API_KEY} 로 주입)
 * @param model          모델 ID. 기본 {@value #DEFAULT_MODEL}
 * @param maxTokens      응답 상한 토큰. 서술은 4문장이므로 크게 잡을 이유가 없다
 * @param requestTimeout 요청 1건당 타임아웃
 * @param totalBudget    참고용 총예산. 실제 예산 판정은 도메인({@code AiService})이 한다
 */
@ConfigurationProperties(prefix = "app.external.anthropic")
public record AnthropicProperties(
    boolean enabled,
    String apiKey,
    String model,
    int maxTokens,
    Duration requestTimeout,
    Duration totalBudget
) {

    /** 이슈 #73 확정 — 두 용도 모두 상위 모델을 쓴다. */
    public static final String DEFAULT_MODEL = "claude-opus-5";

    static final int DEFAULT_MAX_TOKENS = 1024;
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final Duration DEFAULT_TOTAL_BUDGET = Duration.ofSeconds(8);

    public AnthropicProperties {
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        if (requestTimeout == null) {
            requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        }
        if (totalBudget == null) {
            totalBudget = DEFAULT_TOTAL_BUDGET;
        }
    }

    /**
     * 키를 확인한다. {@code enabled=true} 인데 키가 없으면 <b>기동 시점에</b> 실패시킨다 —
     * 조용히 Mock 으로 되돌아가면 "실 API 를 켰다고 믿는 상태로 템플릿 문장이 나가는" 상황이 되고,
     * 그건 로그를 봐야만 알 수 있다. 켜지 않으면 이 메서드는 호출되지 않는다.
     *
     * @return 비어 있지 않은 API 키
     */
    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "app.external.anthropic.enabled=true 인데 api-key 가 비어 있다 "
                    + "(ANTHROPIC_API_KEY 환경변수를 주입하거나 enabled=false 로 두고 Mock 을 쓴다)");
        }
        return apiKey;
    }
}
