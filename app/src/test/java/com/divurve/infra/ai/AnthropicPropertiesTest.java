package com.divurve.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link AnthropicProperties} 기본값·키 검증 테스트 (이슈 #73).
 * 기본값은 확정값이므로 상수를 그대로 비교한다 — 값이 바뀌면 테스트가 먼저 알린다.
 */
class AnthropicPropertiesTest {

    @Test
    void 비어_있는_값은_확정_기본값으로_채운다() {
        AnthropicProperties props = new AnthropicProperties(true, "key", "  ", 0, null, null);

        assertThat(props.model()).isEqualTo(AnthropicProperties.DEFAULT_MODEL);
        assertThat(props.maxTokens()).isEqualTo(AnthropicProperties.DEFAULT_MAX_TOKENS);
        assertThat(props.requestTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.totalBudget()).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void model_이_null_이어도_기본값으로_채운다() {
        assertThat(new AnthropicProperties(true, "key", null, 1, Duration.ofSeconds(1), Duration.ofSeconds(2))
                .model()).isEqualTo(AnthropicProperties.DEFAULT_MODEL);
    }

    @Test
    void 명시한_값은_그대로_둔다() {
        AnthropicProperties props = new AnthropicProperties(
                false, "key", "claude-sonnet-5", 512, Duration.ofSeconds(3), Duration.ofSeconds(4));

        assertThat(props.enabled()).isFalse();
        assertThat(props.model()).isEqualTo("claude-sonnet-5");
        assertThat(props.maxTokens()).isEqualTo(512);
        assertThat(props.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.totalBudget()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void requireApiKey_는_설정된_키를_돌려준다() {
        assertThat(new AnthropicProperties(true, "sk-ant-test", null, 0, null, null).requireApiKey())
                .isEqualTo("sk-ant-test");
    }

    @Test
    void requireApiKey_는_키가_비어_있으면_기동을_실패시킨다() {
        assertThatThrownBy(() -> new AnthropicProperties(true, "  ", null, 0, null, null).requireApiKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");

        assertThatThrownBy(() -> new AnthropicProperties(true, null, null, 0, null, null).requireApiKey())
                .isInstanceOf(IllegalStateException.class);
    }
}
