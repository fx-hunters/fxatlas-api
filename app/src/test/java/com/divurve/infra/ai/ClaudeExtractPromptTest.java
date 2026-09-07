package com.divurve.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.domain.port.EconEventExtractor.ExtractedEvent;
import com.divurve.domain.port.EconEventExtractor.RawArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ClaudeExtractPrompt} 테스트 (이슈 #74).
 *
 * <p>프롬프트 문구는 규약이다 — 그라운딩·미래 날짜 recall 금지·고정 region ENUM·impact 범위가
 * 실제로 프롬프트에 들어 있는지, 응답 파싱이 검증 없이 원시 문자열을 그대로 옮기는지 확인한다.
 */
class ClaudeExtractPromptTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaudeExtractPrompt prompt = new ClaudeExtractPrompt(mapper);

    private static final RawArticle ARTICLE = new RawArticle(
        "demo://sample/test", "2026년 9월 17일 발표 예정.", Instant.parse("2026-09-07T00:00:00Z"));

    @Test
    void system_프롬프트는_그라운딩과_미래_날짜_금지_규약을_담는다() {
        String system = prompt.system();

        assertThat(system).contains("원문에 있는 사실만 옮긴다");
        assertThat(system).contains("미래 날짜 recall");
        assertThat(system).contains("US|EU|JP|KR|CN|GB|GLOBAL");
        assertThat(system).contains("1(낮음)부터 3(높음)");
        assertThat(system).contains("events");
    }

    @Test
    void user_프롬프트는_출처와_원문을_그대로_담는다() {
        String user = prompt.user(ARTICLE);

        assertThat(user).contains("source_url: demo://sample/test");
        assertThat(user).contains("2026년 9월 17일 발표 예정.");
    }

    @Test
    void user는_article이_null이면_실패한다() {
        assertThatThrownBy(() -> prompt.user(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseEvents_는_고정_스키마에서_이벤트를_꺼낸다() {
        List<ExtractedEvent> events = prompt.parseEvents(
            "{\"events\": [{\"event_date\": \"2026-09-17\", \"region\": \"US\", "
                + "\"title\": \"FOMC\", \"impact\": 3}]}");

        assertThat(events).containsExactly(
            new ExtractedEvent("2026-09-17", "US", "FOMC", 3));
    }

    @Test
    void parseEvents_는_여러_건을_모두_꺼낸다() {
        List<ExtractedEvent> events = prompt.parseEvents(
            "{\"events\": ["
                + "{\"event_date\": \"2026-09-17\", \"region\": \"US\", \"title\": \"FOMC\", \"impact\": 3},"
                + "{\"event_date\": \"2026-09-24\", \"region\": \"EU\", \"title\": \"ECB\", \"impact\": 2}"
                + "]}");

        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isEqualTo(new ExtractedEvent("2026-09-24", "EU", "ECB", 2));
    }

    @Test
    void parseEvents_는_빈_events_배열이면_빈_목록을_돌려준다() {
        assertThat(prompt.parseEvents("{\"events\": []}")).isEmpty();
    }

    @Test
    void parseEvents_는_impact가_없으면_null로_담는다() {
        List<ExtractedEvent> events = prompt.parseEvents(
            "{\"events\": [{\"event_date\": \"2026-09-17\", \"region\": \"US\", \"title\": \"FOMC\"}]}");

        assertThat(events.get(0).impact()).isNull();
    }

    @Test
    void parseEvents_는_코드블록으로_감싸_온_응답도_받아준다() {
        String fenced = "```json\n{\"events\": []}\n```";

        assertThat(prompt.parseEvents(fenced)).isEmpty();
    }

    @Test
    void parseEvents_는_닫는_백틱이_없어도_받아준다() {
        String fenced = "```json\n{\"events\": []}";

        assertThat(prompt.parseEvents(fenced)).isEmpty();
    }

    @Test
    void parseEvents_는_한_줄짜리_백틱만_있으면_그대로_파싱을_시도한다() {
        assertThatThrownBy(() -> prompt.parseEvents("```"))
            .isInstanceOf(AiResponseFormatException.class);
    }

    @Test
    void parseEvents_는_빈_응답이면_형식_예외를_던진다() {
        assertThatThrownBy(() -> prompt.parseEvents(null))
            .isInstanceOf(AiResponseFormatException.class)
            .hasMessageContaining("events");
    }

    @Test
    void parseEvents_는_JSON_이_아니면_형식_예외를_던진다() {
        assertThatThrownBy(() -> prompt.parseEvents("알겠습니다."))
            .isInstanceOf(AiResponseFormatException.class)
            .hasMessageContaining("JSON");
    }

    @Test
    void parseEvents_는_events_키가_없으면_형식_예외를_던진다() {
        assertThatThrownBy(() -> prompt.parseEvents("{\"text\": \"...\"}"))
            .isInstanceOf(AiResponseFormatException.class)
            .hasMessageContaining("events");
    }

    @Test
    void parseEvents_는_events가_배열이_아니면_형식_예외를_던진다() {
        assertThatThrownBy(() -> prompt.parseEvents("{\"events\": \"오류\"}"))
            .isInstanceOf(AiResponseFormatException.class)
            .hasMessageContaining("events");
    }

    @Test
    void mapper_가_null_이면_생성에_실패한다() {
        assertThatThrownBy(() -> new ClaudeExtractPrompt(null))
            .isInstanceOf(NullPointerException.class);
    }
}
