package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link RegimeDisclosureCheck} 테스트 ({@code docs/05-ai-usage-v2.md} §5.1, FR-SF-03, 이슈 #73).
 */
class RegimeDisclosureCheckTest {

    private final RegimeDisclosureCheck check = new RegimeDisclosureCheck();

    private static final List<String> DISCLOSED = List.of(
            "현재 환율은 1380원입니다.",
            "최근 시장 변동성이 커진 구간이라 안내한 수치와 구간의 오차가 평소보다 커질 수 있습니다.");

    private static final List<String> SILENT = List.of("현재 환율은 1380원입니다.");

    @Test
    void regime_이_없으면_검사하지_않는다() {
        assertThat(check.verify(SILENT, Map.of("amount", 1000))).isTrue();
    }

    @Test
    void regime_이_평시면_검사하지_않는다() {
        assertThat(check.verify(SILENT, Map.of("regime", "calm"))).isTrue();
    }

    @Test
    void regime_이_문자열이_아니면_검사하지_않는다() {
        assertThat(check.verify(SILENT, Map.of("regime", 3))).isTrue();
    }

    @Test
    void facts_가_null_이면_검사하지_않는다() {
        assertThat(check.verify(SILENT, null)).isTrue();
    }

    @Test
    void elevated_에_안내가_있으면_통과한다() {
        assertThat(check.verify(DISCLOSED, Map.of("regime", "elevated"))).isTrue();
    }

    @Test
    void stress_는_대소문자를_가리지_않는다() {
        assertThat(check.verify(DISCLOSED, Map.of("regime", "STRESS"))).isTrue();
    }

    @Test
    void 급변_구간에_안내가_없으면_실패한다() {
        assertThat(check.verify(SILENT, Map.of("regime", "stress"))).isFalse();
    }

    @Test
    void 변동성만_언급하고_결과를_말하지_않으면_실패한다() {
        List<String> half = List.of("최근 변동성 지표는 5년 백분위 72% 구간입니다.");

        assertThat(check.verify(half, Map.of("regime", "elevated"))).isFalse();
    }

    /**
     * 뜻이 같아도 바꿔 쓴 표현은 통과시키지 않는다 — 실 API 가 explain_level=simple 에서 실제로
     * 만들어낸 문장이다(이슈 #73). 낱말을 넓히면 규약을 안 지킨 응답까지 새어 들어온다.
     */
    @Test
    void 고정_문구를_바꿔_쓰면_실패한다() {
        List<String> paraphrased = List.of(
                "지금은 환율이 평소보다 크게 흔들리는 구간이라, 위에서 안내한 수치와 범위가 "
                        + "실제와 어긋나는 폭이 평소보다 커질 수 있는 점을 감안해서 봐주세요.");

        assertThat(check.verify(paraphrased, Map.of("regime", "stress"))).isFalse();
    }

    @Test
    void 고정_문구가_문장에_그대로_있으면_통과한다() {
        List<String> exact = List.of(
                "환율이 흔들리는 정도가 큰 편이라, " + RegimeDisclosureCheck.REQUIRED_DISCLOSURE + ".");

        assertThat(check.verify(exact, Map.of("regime", "elevated"))).isTrue();
    }

    @Test
    void sentences_가_null_이면_실패한다() {
        assertThat(check.verify(null, Map.of("regime", "stress"))).isFalse();
    }
}
