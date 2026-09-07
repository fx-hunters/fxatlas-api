package com.divurve.infra.ai;

import com.divurve.domain.port.EconEventExtractor;
import com.divurve.domain.port.EconEventExtractor.ExtractedEvent;
import com.divurve.domain.port.EconEventExtractor.RawArticle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 비정형 원문 → 경제 이벤트 추출 프롬프트 조립과 응답 파싱 (이슈 #74,
 * {@code docs/05-ai-usage-v2.md} §3.4·§4).
 *
 * <p>순수 함수만 둔다 — 네트워크도 상태도 없다({@link ClaudeExplainPrompt} 와 같은 설계).
 * 값 검증(타입·ENUM·범위·날짜 신뢰성)은 여기서 하지 않는다 — {@code EconEventValidator}(도메인)의
 * 책임이다. 이 클래스는 모델이 돌려준 원시 문자열을 {@link ExtractedEvent} 로 옮기기만 한다(SRP).
 *
 * <p><b>미래 날짜 recall 금지</b>(이슈 #74 제약 2)는 system 프롬프트에 명문화해 애초에 어기지
 * 않게 한다 — 사후 검증만으로는 이미 나온 값을 버릴 수만 있고, 왜 나왔는지는 막지 못한다.
 */
final class ClaudeExtractPrompt {

    /** 허용 지역 ENUM 문자열 (이슈 #74 제약, ERD {@code econ_events.region}). */
    private static final String ALLOWED_REGIONS = "US|EU|JP|KR|CN|GB|GLOBAL";

    private final ObjectMapper mapper;

    private static final String SYSTEM_PROMPT = """
        당신은 외화 목표·환전 타이밍 서비스의 경제 이벤트 추출 담당이다. 하는 일은 하나뿐이다 —
        아래에 그대로 포함되는 원문에 실제로 등장한 경제 일정만 고정 스키마로 옮겨 적는 것.

        반드시 지킬 것:
        1. 그라운딩 — 원문에 있는 사실만 옮긴다. 원문에 없는 날짜·수치·지표·전망을 절대
           만들어내지 않는다. 원문 밖 지식(웹 검색 결과 포함)을 참조하지 않는다.
        2. 날짜 규약 — event_date 는 반드시 원문에 등장한 날짜여야 한다. 원문에 날짜가 없거나
           불명확하면 그 이벤트는 아예 출력하지 않는다. 오늘 이후의 날짜를 추정해서 채우지
           않는다 — LLM 은 미래 날짜 recall 에 취약하다.
        3. region 은 반드시 다음 중 하나다: %s. 원문 국가를 이 목록으로 판단할 수 없으면
           GLOBAL 로 둔다.
        4. impact 는 1(낮음)부터 3(높음) 사이의 정수다.
        5. 서버 툴(웹 검색 등)을 선언하거나 쓰지 않는다. 확장 사고를 하지 않는다.
        6. 원문에서 이 조건을 만족하는 이벤트를 하나도 찾지 못하면 이벤트를 지어내지 말고
           빈 배열을 출력한다.

        출력 형식 — 아래 JSON 하나만 출력한다. 코드 블록도 설명도 덧붙이지 않는다.
        {"events": [{"event_date": "YYYY-MM-DD", "region": "US", "title": "...", "impact": 1}]}
        """.formatted(ALLOWED_REGIONS);

    ClaudeExtractPrompt(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** 매 호출 동일한 규약 프롬프트. */
    String system() {
        return SYSTEM_PROMPT;
    }

    /**
     * 이번 원문을 그대로 담은 user 메시지. 원문을 문장으로 요약하지 않고 전문을 넘긴다 —
     * 요약 과정에서 날짜·수치가 바뀌면 그라운딩이 깨진다.
     */
    String user(RawArticle article) {
        Objects.requireNonNull(article, "article");
        return """
            source_url: %s
            fetched_at: %s
            원문:
            %s
            """.formatted(article.sourceUrl(), article.fetchedAt(), article.text());
    }

    /**
     * 응답 본문에서 이벤트 후보 목록을 꺼낸다. 값 검증은 하지 않는다 — 원시 문자열 그대로 담는다.
     *
     * @param body 모델 응답 본문
     * @return 추출된 이벤트 후보 목록. 없으면 빈 목록
     * @throws AiResponseFormatException 고정 스키마({@code {"events": [...]}})가 아닐 때
     */
    List<ExtractedEvent> parseEvents(String body) {
        JsonNode root = readTree(body);
        JsonNode events = root.path("events");
        if (!events.isArray()) {
            throw new AiResponseFormatException("응답에 events 배열이 없다");
        }

        List<ExtractedEvent> parsed = new ArrayList<>(events.size());
        for (JsonNode node : events) {
            parsed.add(toExtractedEvent(node));
        }
        return List.copyOf(parsed);
    }

    private ExtractedEvent toExtractedEvent(JsonNode node) {
        String eventDate = node.path("event_date").asText(null);
        String region = node.path("region").asText(null);
        String title = node.path("title").asText(null);
        JsonNode impactNode = node.path("impact");
        Integer impact = impactNode.isInt() ? impactNode.intValue() : null;
        return new EconEventExtractor.ExtractedEvent(eventDate, region, title, impact);
    }

    private JsonNode readTree(String body) {
        try {
            return mapper.readTree(stripCodeFence(body));
        } catch (JsonProcessingException e) {
            throw new AiResponseFormatException("응답이 JSON 이 아니다", e);
        }
    }

    /**
     * 코드 블록으로 감싸 온 응답을 벗긴다 ({@link ClaudeExplainPrompt#stripCodeFence} 와 동일한
     * 패턴). system 프롬프트가 "코드 블록을 덧붙이지 말라"고 지시하지만, 지시를 어겼을 때
     * 파싱 실패로 배치 전체를 버리는 것보다 벗겨서 쓰는 편이 낫다.
     */
    private static String stripCodeFence(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return trimmed;
        }
        String withoutOpening = trimmed.substring(firstLineEnd + 1);
        int closing = withoutOpening.lastIndexOf("```");
        return (closing < 0 ? withoutOpening : withoutOpening.substring(0, closing)).trim();
    }
}
