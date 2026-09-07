package com.divurve.infra.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.domain.port.EconEventExtractor.RawArticle;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * {@link NoOpEconEventExtractor} 테스트 (이슈 #74). 항상 빈 목록을 돌려주고, 원문이 없으면
 * 방어적으로 실패하는지 확인한다.
 */
class NoOpEconEventExtractorTest {

    private final NoOpEconEventExtractor sut = new NoOpEconEventExtractor();

    @Test
    void extract_는_항상_빈_목록을_돌려준다() {
        RawArticle article = new RawArticle("demo://sample/x", "본문", Instant.EPOCH);

        assertThat(sut.extract(article)).isEmpty();
    }

    @Test
    void extract_는_article이_null이면_실패한다() {
        assertThatThrownBy(() -> sut.extract(null)).isInstanceOf(NullPointerException.class);
    }
}
