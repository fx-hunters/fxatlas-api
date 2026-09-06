package com.divurve.domain.ai;

import com.divurve.common.architecture.UseCase;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 급변 구간에서 불확실성 확대 안내가 실제로 문장에 들어갔는지 확인한다
 * ({@code docs/05-ai-usage-v2.md} §5.1, FR-SF-03).
 *
 * <p><b>왜 필요해졌나</b>(이슈 #73 제약 7) — 이 규약은 그동안 {@code MockAiProvider} 가
 * 문자열 상수를 조건부로 끼워 넣어 <b>구조적으로</b> 만족시키고 있었다. 서술을 실 LLM 에 맡기면
 * 그 보장이 사라진다. 프롬프트에 규약을 적는 것만으로는 지켜졌는지 알 수 없고, 안 지켜져도
 * 아무 검사에 걸리지 않아 <b>조용히</b> 깨진다 — 하필 가장 필요한 급변 구간에서.
 *
 * <p>{@code regime} 이 평시({@code calm} 등)이거나 없으면 이 검사는 항상 통과한다.
 */
@UseCase
public class RegimeDisclosureCheck {

    /** §5.1 급변 상태. */
    static final String REGIME_ELEVATED = "elevated";
    static final String REGIME_STRESS = "stress";

    /**
     * 급변 구간에 반드시 그대로 들어가야 하는 안내 문구.
     *
     * <p><b>왜 낱말 대조가 아니라 고정 문구인가</b>(이슈 #73, 실 API 응답 3회로 확인) —
     * {@code explain_level=simple} 에서 모델은 매번 다르게 풀어 쓴다: "환율이 움직이는 폭",
     * "크게 흔들리는 구간", "실제와 어긋나는 폭이 평소보다 커질 수 있는". 뜻은 다 맞는데 낱말이
     * 매번 달라, 키워드를 넓힐수록 규약을 안 지킨 응답까지 통과시키게 된다.
     *
     * <p>§5.1 안내는 표현을 다듬을 문장이 아니라 <b>고지해야 할 문구</b>다. 그래서 프롬프트가
     * 이 문구를 그대로 넣으라고 지시하고({@code ClaudeExplainPrompt}), 여기서 그대로 들어왔는지
     * 확인한다. {@code MockAiProvider} 의 상수도 이 문구를 포함한다 — 두 경로가 같은 문장을 낸다.
     */
    public static final String REQUIRED_DISCLOSURE = "안내한 수치와 구간의 오차가 평소보다 커질 수 있습니다";

    /**
     * 급변 구간이라면 불확실성 확대 안내가 포함됐는지 본다.
     *
     * @param sentences 서술 문장들
     * @param facts     엔진이 만든 사실. {@code regime} 키만 본다
     * @return 규약을 지켰으면 {@code true}. 평시이면 검사 없이 {@code true}
     */
    public boolean verify(List<String> sentences, Map<String, Object> facts) {
        if (!isWidenRegime(facts)) {
            return true;
        }
        String joined = String.join(" ", sentences == null ? List.<String>of() : sentences);
        return joined.contains(REQUIRED_DISCLOSURE);
    }

    private static boolean isWidenRegime(Map<String, Object> facts) {
        Object regime = facts == null ? null : facts.get("regime");
        String value = regime instanceof String s ? s.toLowerCase(Locale.ROOT) : null;
        return REGIME_ELEVATED.equals(value) || REGIME_STRESS.equals(value);
    }
}
