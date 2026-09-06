package com.divurve.engine.riskprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link DetailDiagnosisMapper} 단위 테스트 (API 명세 v2 §5.2, FR-DG-04·FR-DG-05).
 * 상세 진단은 점수를 만들지 않는다 — 여기서 검증하는 것은 완료 여부·재개 커서·제목 수식어뿐이다.
 */
class DetailDiagnosisMapperTest {

    private final DetailDiagnosisMapper mapper = new DetailDiagnosisMapper();

    @Test
    void Q4에서_Q6을_모두_채워야_완료다() {
        assertThat(mapper.isComplete(Map.of("q4", "B", "q5", "standard", "q6", "finance"))).isTrue();
        assertThat(mapper.isComplete(Map.of("q4", "B", "q5", "standard"))).isFalse();
        assertThat(mapper.isComplete(Map.of())).isFalse();
        assertThat(mapper.isComplete(null)).isFalse();
    }

    @Test
    void 첫_미응답_문항을_재개_커서로_돌려준다() {
        assertThat(mapper.nextQuestion(Map.of())).isEqualTo("q4");
        assertThat(mapper.nextQuestion(Map.of("q4", "B"))).isEqualTo("q5");
        assertThat(mapper.nextQuestion(Map.of("q4", "B", "q5", "standard"))).isEqualTo("q6");
        assertThat(mapper.nextQuestion(Map.of("q4", "B", "q5", "standard", "q6", "finance"))).isNull();
    }

    @Test
    void 공백_응답은_미응답으로_본다() {
        Map<String, String> blank = new HashMap<>();
        blank.put("q4", "  ");
        blank.put("q5", null);
        assertThat(mapper.nextQuestion(blank)).isEqualTo("q4");
        assertThat(mapper.isComplete(blank)).isFalse();
    }

    @Test
    void Q4_응답에서_제목_수식어를_만든다() {
        assertThat(mapper.titleModifier("B")).isEqualTo("지출 균형을 함께 고려하는"); // 명세 §5.1 예시
        assertThat(mapper.titleModifier("a")).isEqualTo("생활자금과 투자자금을 함께 굴리는"); // 소문자 허용
        assertThat(mapper.titleModifier("C")).isEqualTo("생활자금을 따로 떼어 두는");
        assertThat(mapper.titleModifier("D")).isEqualTo("생활자금과 투자자금을 확실히 나누는");
    }

    @Test
    void Q4_미응답이면_수식어가_없다() {
        assertThat(mapper.titleModifier(null)).isNull();
        assertThat(mapper.titleModifier("  ")).isNull();
    }

    @Test
    void Q4_선택지가_A에서_D_밖이면_예외() {
        assertThatThrownBy(() -> mapper.titleModifier("Z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A~D");
    }
}
