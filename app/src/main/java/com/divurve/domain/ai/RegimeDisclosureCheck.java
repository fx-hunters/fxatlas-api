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

    /** "변동성" 언급만으로는 부족하다 — 그 결과(오차가 커진다)까지 말해야 안내가 된다. */
    private static final String VOLATILITY_TERM = "변동성";
    private static final List<String> WIDENING_TERMS = List.of("오차", "불확실", "커질 수");

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
        return joined.contains(VOLATILITY_TERM)
            && WIDENING_TERMS.stream().anyMatch(joined::contains);
    }

    private static boolean isWidenRegime(Map<String, Object> facts) {
        Object regime = facts == null ? null : facts.get("regime");
        String value = regime instanceof String s ? s.toLowerCase(Locale.ROOT) : null;
        return REGIME_ELEVATED.equals(value) || REGIME_STRESS.equals(value);
    }
}
