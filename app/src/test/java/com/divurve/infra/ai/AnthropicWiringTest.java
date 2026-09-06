package com.divurve.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.port.AiProvider;
import com.divurve.infra.config.AnthropicConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 실 어댑터와 Mock 의 빈 배선 테스트 (이슈 #73 제약 1, 이슈 #38 과 같은 유형).
 *
 * <p><b>왜 컨텍스트를 띄워 보나</b> — {@code AiProvider} 구현체가 둘이 되는 순간이 여기다.
 * 단위 테스트로는 {@code @ConditionalOnProperty}·{@code @Primary} 가 실제로 먹는지 알 수 없고,
 * 틀렸을 때 나타나는 증상은 컴파일 오류가 아니라 <b>기동 실패</b>다.
 *
 * <p>{@code ApplicationContextSmokeTest} 에 프로퍼티 오버라이드를 더하지 말라는 그 클래스의 규칙을
 * 지키기 위해 전체 컨텍스트 대신 {@link ApplicationContextRunner} 를 쓴다 — 컨텍스트 캐시가
 * 갈라지지 않는다.
 */
class AnthropicWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(ObjectMapper.class)
            .withUserConfiguration(MockAiProvider.class, ClaudeAiProvider.class, AnthropicConfig.class);

    @Test
    void 기본값에서는_Mock_하나만_뜬다() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AiProvider.class);
            assertThat(context.getBean(AiProvider.class)).isInstanceOf(MockAiProvider.class);
            assertThat(context).doesNotHaveBean(ClaudeMessageClient.class);
        });
    }

    @Test
    void 켜면_두_구현체가_공존하고_실_어댑터가_주입된다() {
        runner.withPropertyValues(
                        "app.external.anthropic.enabled=true",
                        "app.external.anthropic.api-key=sk-ant-test")
                .run(context -> {
                    // 기동이 실패하지 않는다 — @Primary 가 NoUniqueBeanDefinitionException 을 막는다.
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(AiProvider.class)).hasSize(2);
                    assertThat(context.getBean(AiProvider.class)).isInstanceOf(ClaudeAiProvider.class);
                    // Mock 은 살아남아 forecast_summary 이외 화면을 계속 담당한다.
                    assertThat(context).hasSingleBean(MockAiProvider.class);
                });
    }

    @Test
    void 켰는데_키가_없으면_기동에_실패한다() {
        runner.withPropertyValues("app.external.anthropic.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("api-key"));
    }
}
