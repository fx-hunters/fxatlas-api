package com.divurve.engine.riskprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link RiskProfileScorer} 단위 테스트 (API 명세 v2 §5.1, FR-DG-02·FR-DG-06·FR-DG-07).
 *
 * <p>고정하는 계약 두 가지.
 * <ol>
 *   <li><b>Q1~Q3 을 모두 답해야 유형이 나온다</b> — 하나라도 비면 {@link Optional#empty()}(=미측정)이며
 *       임의의 기본 성향을 만들지 않는다. (이전 버전은 1~2문항만으로도 등급을 냈다.)</li>
 *   <li>등급 경계 0~2 / 3~4 / 5~6 / 7~9 를 양끝 값으로 고정한다.</li>
 * </ol>
 */
class RiskProfileScorerTest {

    private final RiskProfileScorer scorer = new RiskProfileScorer();

    private static Map<String, String> answers(String q1, String q2, String q3) {
        Map<String, String> map = new HashMap<>();
        map.put("q1", q1);
        map.put("q2", q2);
        map.put("q3", q3);
        return map;
    }

    private RiskAssessment assess(String q1, String q2, String q3) {
        return scorer.assess(answers(q1, q2, q3)).orElseThrow();
    }

    @Test
    void 선택지_코드를_점수로_바꾼다() {
        assertThat(scorer.points("A")).isEqualTo(RiskProfileScorer.MIN_POINTS);
        assertThat(scorer.points("B")).isEqualTo(1);
        assertThat(scorer.points("C")).isEqualTo(2);
        assertThat(scorer.points("d")).isEqualTo(RiskProfileScorer.MAX_POINTS); // 소문자 허용
    }

    @Test
    void 선택지가_A에서_D_밖이면_예외() {
        assertThatThrownBy(() -> scorer.points("E")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scorer.points(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 응답값을_합해_원점수를_낸다() {
        assertThat(assess("A", "A", "A").score()).isZero();
        assertThat(assess("B", "C", "D").score()).isEqualTo(6);
        assertThat(assess("D", "D", "D").score()).isEqualTo(9);
    }

    @Test
    void 합계_0에서_2는_stable() {
        assertThat(assess("A", "A", "A").riskType()).isEqualTo(RiskProfileScorer.STABLE);
        assertThat(assess("B", "B", "A").riskType()).isEqualTo(RiskProfileScorer.STABLE); // 경계 2
        assertThat(assess("A", "A", "A").gradeLabel()).isEqualTo("안정항로형");
    }

    @Test
    void 합계_3에서_4는_balanced() {
        assertThat(assess("B", "B", "B").riskType()).isEqualTo(RiskProfileScorer.BALANCED); // 경계 3
        RiskAssessment boundary4 = assess("B", "C", "B"); // 경계 4 — 명세 §4 Mock fixture
        assertThat(boundary4.riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(boundary4.gradeLabel()).isEqualTo("균형항로형");
        assertThat(boundary4.concentrationThreshold()).isEqualTo(0.60);
        assertThat(boundary4.safeRatioAdjust()).isEqualTo(0.00);
    }

    @Test
    void 합계_5에서_6은_aggressive() {
        assertThat(assess("C", "C", "B").riskType()).isEqualTo(RiskProfileScorer.AGGRESSIVE); // 경계 5
        assertThat(assess("C", "C", "C").riskType()).isEqualTo(RiskProfileScorer.AGGRESSIVE); // 경계 6
        assertThat(assess("C", "C", "C").gradeLabel()).isEqualTo("적극항로형");
    }

    @Test
    void 합계_7에서_9는_challenging() {
        assertThat(assess("D", "C", "C").riskType()).isEqualTo(RiskProfileScorer.CHALLENGING); // 경계 7
        assertThat(assess("D", "D", "D").riskType()).isEqualTo(RiskProfileScorer.CHALLENGING); // 최대 9
        assertThat(assess("D", "D", "D").gradeLabel()).isEqualTo("도전항로형");
    }

    @Test
    void 문항이_하나라도_미응답이면_미측정이다() {
        assertThat(scorer.assess(Map.of("q1", "B", "q2", "C"))).isEmpty();
        assertThat(scorer.assess(Map.of("q1", "B"))).isEmpty();
        assertThat(scorer.assess(Map.of())).isEmpty();
        assertThat(scorer.assess(null)).isEmpty();
        assertThat(scorer.assess(answers("B", "C", "  "))).isEmpty(); // 공백은 미응답
        assertThat(scorer.assess(answers("B", "C", null))).isEmpty();
    }

    @Test
    void 미응답_여부를_따로_알려준다() {
        assertThat(scorer.isSimpleComplete(answers("B", "C", "B"))).isTrue();
        assertThat(scorer.isSimpleComplete(Map.of("q1", "B"))).isFalse();
        assertThat(scorer.isSimpleComplete(null)).isFalse();
    }

    @Test
    void 응답에_허용되지_않는_선택지가_있으면_예외() {
        assertThatThrownBy(() -> scorer.assess(answers("B", "C", "Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A~D");
    }

    @Test
    void 문항별_판정_근거를_명세_예시대로_만든다() {
        RiskAssessment assessment = assess("B", "C", "B");

        assertThat(assessment.rationale()).containsExactly(
                new RiskRationale("q1", "B", 1, "작은 손실은 받아들이지만 커지면 불편하게 느낍니다."),
                new RiskRationale("q2", "C", 2, "손실 가능성이 커져도 더 높은 수익을 기대하는 쪽입니다."),
                new RiskRationale("q3", "B", 1, "자산 금액이 조금씩 오르내리는 정도는 괜찮게 느낍니다."));
    }

    @Test
    void 상충_응답은_새_유형을_만들지_않고_보조_설명만_남긴다() {
        // A-D-A: 문항 간 점수 폭 3 → 상충 (화면 v2 §8 예시). 유형은 합계 3 그대로 balanced 다.
        RiskAssessment mixed = assess("A", "D", "A");
        assertThat(mixed.riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(mixed.mixedResponseNote()).isEqualTo(RiskProfileScorer.MIXED_RESPONSE_NOTE);

        // 폭 2 는 상충이 아니다 (경계).
        assertThat(assess("A", "C", "A").mixedResponseNote()).isNull();
    }

    @Test
    void 판정_근거_목록은_불변이다() {
        RiskAssessment assessment = assess("B", "C", "B");
        assertThatThrownBy(() -> assessment.rationale().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
