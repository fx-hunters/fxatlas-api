package com.divurve.engine.riskprofile;

import com.divurve.engine.EngineComponent;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 상세 진단(Q4~Q6) 매핑기 (API 명세 v2 §5.2, 화면 정의서 v2 §9·§10, 요구사항 v2 §4.3).
 *
 * <p><b>점수를 만들지 않는다</b> — 상세 진단은 대표 유형·원점수를 어떤 경우에도 바꾸지 않는다(FR-DG-05).
 * 여기서 만드는 것은 (1) 제목 수식어(Q4), (2) 중단·재개용 다음 문항 커서, (3) 완료 여부뿐이다.
 *
 * <ul>
 *   <li>Q4 — 생활자금과 외화·투자자금 분리 → 결과 제목 수식어</li>
 *   <li>Q5 — 원하는 설명 방식 → {@code user_settings.explain_level} (값 검증은 domain 책임)</li>
 *   <li>Q6 — 실제 보유 경험/익숙한 분야 → {@code user_settings.explain_domain} (값 검증은 domain 책임)</li>
 * </ul>
 */
@EngineComponent
public class DetailDiagnosisMapper {

    /** 상세 진단 문항 코드 (순서 고정). 재개 커서는 이 순서에서 첫 미응답 문항이다. */
    public static final List<String> DETAIL_QUESTIONS = List.of("q4", "q5", "q6");

    /**
     * Q4 선택지 → 결과 제목 수식어. {@code B} 는 명세 §5.1 예시 그대로이고, 나머지 셋은 문서 근거가 없어
     * "분리하지 않음(A) → 완전 분리(D)" 순서를 가정해 채웠다 (미확정).
     */
    private static final Map<String, String> TITLE_MODIFIER_BY_Q4 = Map.of(
            "A", "생활자금과 투자자금을 함께 굴리는",
            "B", "지출 균형을 함께 고려하는",
            "C", "생활자금을 따로 떼어 두는",
            "D", "생활자금과 투자자금을 확실히 나누는");

    /** Q4~Q6 이 모두 응답됐는지 본다. 모두 응답되면 {@code detail_done} 이다. */
    public boolean isComplete(Map<String, String> answered) {
        return DETAIL_QUESTIONS.stream().allMatch(q -> isAnswered(answered, q));
    }

    /**
     * 중단 지점에서 이어서 물을 문항을 돌려준다 (FR-DG-04, ERD {@code detail_progress}).
     *
     * @return 첫 미응답 문항 코드. 모두 응답됐으면 {@code null}
     */
    public String nextQuestion(Map<String, String> answered) {
        return DETAIL_QUESTIONS.stream()
                .filter(q -> !isAnswered(answered, q))
                .findFirst()
                .orElse(null);
    }

    /**
     * Q4 응답에서 결과 제목 수식어를 만든다. <b>점수에는 영향이 없다</b>(FR-DG-05).
     *
     * @param q4Choice Q4 선택지 코드 {@code A}~{@code D}. 미응답이면 {@code null}
     * @return 제목 수식어. Q4 미응답이면 {@code null}
     * @throws IllegalArgumentException 허용 선택지가 아닌 경우
     */
    public String titleModifier(String q4Choice) {
        if (q4Choice == null || q4Choice.isBlank()) {
            return null;
        }
        String modifier = TITLE_MODIFIER_BY_Q4.get(q4Choice.trim().toUpperCase(Locale.ROOT));
        if (modifier == null) {
            throw new IllegalArgumentException("Q4 선택지는 A~D 여야 합니다 (입력 " + q4Choice + ").");
        }
        return modifier;
    }

    private boolean isAnswered(Map<String, String> answered, String question) {
        if (answered == null) {
            return false;
        }
        String choice = answered.get(question);
        return choice != null && !choice.isBlank();
    }
}
