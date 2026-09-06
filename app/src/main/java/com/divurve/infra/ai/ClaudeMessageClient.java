package com.divurve.infra.ai;

/**
 * Claude Messages API 호출 시임 (이슈 #73).
 *
 * <p><b>왜 인터페이스로 한 겹 감쌌나</b> — Anthropic SDK 클라이언트를 어댑터가 직접 들면
 * {@code ClaudeAiProvider} 를 네트워크 없이 테스트할 수 없고, 커버리지 100% 게이트(CLAUDE.md 8장)를
 * 통과할 방법이 없어진다. 프롬프트 조립·응답 파싱·실패 처리는 전부 어댑터 쪽에 두고, 이 시임은
 * "system·user 를 보내면 텍스트가 돌아온다"만 담당한다.
 *
 * <p>구현체({@link AnthropicMessageClient})는 {@code AnthropicConfig} 가 {@code @Bean} 으로 등록한다.
 */
public interface ClaudeMessageClient {

    /**
     * 한 번의 Messages 호출.
     *
     * @param systemPrompt 그라운딩 규약(§5 1단계) — 매 호출 동일
     * @param userPrompt   이번 서술 요청의 {@code facts} 와 설명 선호
     * @return 응답 본문 텍스트와 토큰 사용량
     * @throws RuntimeException 타임아웃·429·5xx 등 API 실패. 호출자는 즉시 폴백한다(FR-AI-06)
     */
    Completion complete(String systemPrompt, String userPrompt);

    /**
     * 호출 결과.
     *
     * @param text         응답 텍스트 블록을 이어붙인 본문
     * @param inputTokens  입력 토큰 수 (감사 기록용 메타)
     * @param outputTokens 출력 토큰 수 (감사 기록용 메타)
     */
    record Completion(String text, long inputTokens, long outputTokens) {
    }
}
