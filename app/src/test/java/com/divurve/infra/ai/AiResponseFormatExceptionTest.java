package com.divurve.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link AiResponseFormatException} 생성자 테스트 (이슈 #73). */
class AiResponseFormatExceptionTest {

    @Test
    void 메시지만_담을_수_있다() {
        AiResponseFormatException e = new AiResponseFormatException("형식 위반");

        assertThat(e).hasMessage("형식 위반").hasNoCause();
    }

    @Test
    void 원인을_함께_담을_수_있다() {
        IllegalStateException cause = new IllegalStateException("파싱 실패");
        AiResponseFormatException e = new AiResponseFormatException("형식 위반", cause);

        assertThat(e).hasMessage("형식 위반").hasCause(cause);
    }
}
