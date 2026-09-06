package com.divurve.infra.ai;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.ai.AiService;
import com.divurve.domain.port.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;

/**
 * Anthropic Claude 를 실제로 호출하는 {@link AiProvider} 어댑터 (이슈 #73).
 *
 * <p><b>범위는 {@code forecast_summary} 하나다.</b> 문서상 프롬프트 규약이 확정된 화면이 여기뿐이기
 * 때문이다({@code docs/05-ai-usage-v2.md} §3.2 — 4문장 고정). 나머지 화면(홈·X-Ray·Fit·스트레스)은
 * 규약이 정해질 때까지 {@link MockAiProvider} 의 템플릿을 그대로 쓴다. 규약 없이 실 API 를 붙이면
 * 문장 수·어조가 화면마다 흔들리고, 그걸 잡아낼 검증 항목도 없다.
 *
 * <p><b>빈 충돌</b>(이슈 #38 과 같은 유형) — {@code app.external.anthropic.enabled=true} 일 때만
 * 생성되고, 그때는 {@link MockAiProvider} 와 함께 두 개의 {@code AiProvider} 빈이 존재한다.
 * {@code @Primary} 로 이쪽이 주입되고, Mock 은 아래 {@code templateProvider} 로 들어와 범위 밖 화면을
 * 계속 담당한다. 꺼져 있으면 이 클래스는 아예 만들어지지 않으므로 Mock 하나만 남는다.
 *
 * <p><b>여기서 예외를 삼키지 않는다.</b> 타임아웃·429·5xx·형식 위반은 그대로 던지고,
 * 폴백 판단은 {@code AiService} 가 한다 — 폴백은 도메인 정책이지 어댑터의 재량이 아니다.
 */
@ExternalAdapter
@Primary
@ConditionalOnProperty(prefix = "app.external.anthropic", name = "enabled", havingValue = "true")
public class ClaudeAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiProvider.class);

    private final ClaudeMessageClient messageClient;
    private final AiProvider templateProvider;
    private final ClaudeExplainPrompt prompt;
    private final AnthropicProperties props;

    public ClaudeAiProvider(
        ClaudeMessageClient messageClient,
        @Qualifier("mockAiProvider") AiProvider templateProvider,
        ObjectMapper objectMapper,
        AnthropicProperties props
    ) {
        this.messageClient = Objects.requireNonNull(messageClient, "messageClient");
        this.templateProvider = Objects.requireNonNull(templateProvider, "templateProvider");
        this.prompt = new ClaudeExplainPrompt(Objects.requireNonNull(objectMapper, "objectMapper"));
        this.props = Objects.requireNonNull(props, "props");
    }

    @Override
    public ExplainResult explain(ExplainContext context) {
        Objects.requireNonNull(context, "context");
        if (!AiService.SURFACE_FORECAST_SUMMARY.equals(context.surface())) {
            return templateProvider.explain(context);
        }

        ClaudeMessageClient.Completion completion =
            messageClient.complete(prompt.system(), prompt.user(context));

        List<String> sentences = prompt.parseSentences(completion.text());
        if (sentences.size() != ClaudeExplainPrompt.FORECAST_SENTENCE_COUNT) {
            throw new AiResponseFormatException(
                "forecast_summary 는 %d문장이어야 하는데 %d문장이 왔다"
                    .formatted(ClaudeExplainPrompt.FORECAST_SENTENCE_COUNT, sentences.size()));
        }

        logCallMetadata(context, completion);
        return new ExplainResult(sentences);
    }

    /**
     * 감사 기록의 <b>잠정</b> 형태 (이슈 #73 "열어두는 결정", 이슈 #56).
     *
     * <p>{@code audit_logs} 테이블에 프롬프트 전문을 남길지는 마스킹 범위가 정해지지 않아 보류했다 —
     * {@code facts} 에는 보유 자산 평가액이 들어간다. 그때까지는 <b>페이로드 없이 호출 메타만</b>
     * 로그로 남긴다. 없는 것보다 낫고, 개인정보를 먼저 흘리지도 않는다.
     */
    private void logCallMetadata(ExplainContext context, ClaudeMessageClient.Completion completion) {
        log.info("ai_explained surface={} explain_level={} explain_domain={} model={} "
                + "input_tokens={} output_tokens={}",
            context.surface(), context.explainLevel(), context.explainDomain(), props.model(),
            completion.inputTokens(), completion.outputTokens());
    }
}
