package com.divurve.infra.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.port.EconEventExtractor.RawArticle;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link MockRawArticleSource} 테스트 (이슈 #74).
 *
 * <p>확인하는 것: (1) 시연용임이 드러나는 {@code demo://} 출처인지(FR-CM-10 출처 날조 금지),
 * (2) 원문마다 검증기가 대조할 실제 날짜 문자열이 포함되어 있는지.
 */
class MockRawArticleSourceTest {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}년 \\d{1,2}월 \\d{1,2}일");

    private final MockRawArticleSource sut = new MockRawArticleSource();

    @Test
    void fetchRecent_는_시연용_출처의_원문을_돌려준다() {
        List<RawArticle> articles = sut.fetchRecent();

        assertThat(articles).isNotEmpty();
        assertThat(articles).allSatisfy(article ->
            assertThat(article.sourceUrl()).startsWith("demo://sample/"));
    }

    @Test
    void fetchRecent_의_각_원문은_날짜_문자열을_포함한다() {
        List<RawArticle> articles = sut.fetchRecent();

        assertThat(articles).allSatisfy(article ->
            assertThat(DATE_PATTERN.matcher(article.text()).find()).isTrue());
    }
}
