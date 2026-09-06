package com.divurve.engine.riskprofile;

import com.divurve.engine.EngineComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 간편 진단(Q1~Q3) 등급 산출기 (API 명세 v2 §5.1, 요구사항 v2 §4.3, ERD v3.0 {@code risk_profiles}).
 * 선택지 코드(A=0·B=1·C=2·D=3)의 <b>합계</b>로 원점수(0~9)와 대표 유형 4종을 결정론적으로 계산한다.
 *
 * <ul>
 *   <li>합계 0~2 → {@code stable}(안정항로형)</li>
 *   <li>합계 3~4 → {@code balanced}(균형항로형)</li>
 *   <li>합계 5~6 → {@code aggressive}(적극항로형)</li>
 *   <li>합계 7~9 → {@code challenging}(도전항로형)</li>
 * </ul>
 *
 * <p><b>Q1~Q3 을 전부 응답해야 유형이 나온다</b>(FR-DG-02). 하나라도 비어 있으면 {@link #assess(Map)} 이
 * {@link Optional#empty()} 를 돌려주고 호출자는 {@code not_measured}(미측정)로 응답한다 — 임의의 기본 성향을
 * 채워 넣지 않는다(FR-IS-06). 이전 버전은 1~2문항만으로도 등급을 냈다.
 *
 * <p>이 클래스는 순수 계산만 한다 — Spring/JPA 의존이 없고 부작용이 없다.
 */
@EngineComponent
public class RiskProfileScorer {

    /** 간편 진단 문항 코드 (순서 고정). 이 셋이 모두 응답돼야 유형이 나온다. */
    public static final List<String> SIMPLE_QUESTIONS = List.of("q1", "q2", "q3");

    /** 선택지당 최소 점수 (A). */
    public static final int MIN_POINTS = 0;
    /** 선택지당 최대 점수 (D). */
    public static final int MAX_POINTS = 3;

    /** 안정 유형 상한(합계). */
    public static final int STABLE_MAX_SCORE = 2;
    /** 균형 유형 상한(합계). */
    public static final int BALANCED_MAX_SCORE = 4;
    /** 적극 유형 상한(합계). */
    public static final int AGGRESSIVE_MAX_SCORE = 6;

    /** 안정항로형 유형 코드. */
    public static final String STABLE = "stable";
    /** 균형항로형 유형 코드. */
    public static final String BALANCED = "balanced";
    /** 적극항로형 유형 코드. */
    public static final String AGGRESSIVE = "aggressive";
    /** 도전항로형 유형 코드. */
    public static final String CHALLENGING = "challenging";

    /**
     * 상충 응답 판정 기준 — 문항 간 점수 폭이 이 값 이상이면 상충으로 본다.
     * 화면 정의서 v2 §8 이 드는 유일한 예시 {@code A-D-A}(폭 3)를 그대로 기준으로 삼았다.
     */
    public static final int MIXED_RESPONSE_SPREAD = 3;

    /** 상충 응답 보조 설명 — 새 유형을 만들지 않는다(FR-DG-06). */
    public static final String MIXED_RESPONSE_NOTE =
            "문항별 응답의 방향이 크게 엇갈립니다. 대표 유형은 합계로만 정하며, 엇갈린 응답은 새 유형을 만들지 않고 참고 설명으로만 남깁니다.";

    /** 선택지 코드 → 점수 (A=0·B=1·C=2·D=3). */
    private static final Map<String, Integer> POINTS_BY_CHOICE =
            Map.of("A", 0, "B", 1, "C", 2, "D", 3);

    /** 유형 코드 → 한글 표기. */
    private static final Map<String, String> LABEL_BY_GRADE = Map.of(
            STABLE, "안정항로형",
            BALANCED, "균형항로형",
            AGGRESSIVE, "적극항로형",
            CHALLENGING, "도전항로형");

    /**
     * 유형 코드 → 집중도 참고 기준선. {@code balanced} 0.60 은 명세 §4 Mock fixture 확정값이고,
     * 나머지 셋은 문서 근거가 없어 0.10 등간격으로 가정했다 (미확정).
     */
    private static final Map<String, Double> THRESHOLD_BY_GRADE = Map.of(
            STABLE, 0.50,
            BALANCED, 0.60,
            AGGRESSIVE, 0.70,
            CHALLENGING, 0.80);

    /**
     * 유형 코드 → 안전 버킷 가감. ERD {@code risk_profiles.safe_ratio_adjust} 컬럼을 채우기 위한 값으로,
     * 산출 로직 자체가 ERD §3 "v3 열린 항목"이라 문서 근거가 없다 — 균형을 0 으로 둔 가정값이다 (미확정).
     */
    private static final Map<String, Double> SAFE_RATIO_ADJUST_BY_GRADE = Map.of(
            STABLE, 0.10,
            BALANCED, 0.00,
            AGGRESSIVE, -0.05,
            CHALLENGING, -0.10);

    /**
     * 문항·선택지 → 사용자 언어 해석 문장. {@code q1:B}·{@code q2:C}·{@code q3:B} 세 문장은 명세 §5.1 예시
     * 그대로이고, 나머지 아홉 문장은 같은 어조로 채운 가정값이다 (미확정).
     */
    private static final Map<String, String> READING_BY_ANSWER = Map.ofEntries(
            Map.entry("q1:A", "원금이 줄어드는 상황 자체를 피하고 싶어 합니다."),
            Map.entry("q1:B", "작은 손실은 받아들이지만 커지면 불편하게 느낍니다."),
            Map.entry("q1:C", "일정 폭의 손실은 과정의 일부로 받아들이는 쪽입니다."),
            Map.entry("q1:D", "손실이 커지는 국면도 감수할 수 있다고 답했습니다."),
            Map.entry("q2:A", "수익보다 원금이 지켜지는 쪽을 분명히 앞세웁니다."),
            Map.entry("q2:B", "안정을 먼저 보되 약간의 수익 기회는 함께 살핍니다."),
            Map.entry("q2:C", "손실 가능성이 커져도 더 높은 수익을 기대하는 쪽입니다."),
            Map.entry("q2:D", "수익 기회를 가장 앞에 두고 판단합니다."),
            Map.entry("q3:A", "자산 금액이 흔들리는 것을 보면 크게 신경이 쓰입니다."),
            Map.entry("q3:B", "자산 금액이 조금씩 오르내리는 정도는 괜찮게 느낍니다."),
            Map.entry("q3:C", "자산 금액이 꽤 움직여도 크게 흔들리지 않습니다."),
            Map.entry("q3:D", "자산 금액이 크게 움직여도 그대로 두는 편입니다."));

    /**
     * 선택지 코드의 점수를 돌려준다.
     *
     * @param choice 선택지 코드 {@code A}~{@code D} (소문자 허용)
     * @throws IllegalArgumentException 허용 선택지가 아닌 경우
     */
    public int points(String choice) {
        String normalized = normalize(choice);
        Integer points = normalized == null ? null : POINTS_BY_CHOICE.get(normalized);
        if (points == null) {
            throw new IllegalArgumentException("선택지는 A~D 여야 합니다 (입력 " + choice + ").");
        }
        return points;
    }

    /** Q1~Q3 이 모두 응답됐는지 본다. 하나라도 비어 있으면 유형을 만들지 않는다(FR-DG-02). */
    public boolean isSimpleComplete(Map<String, String> answers) {
        return SIMPLE_QUESTIONS.stream().allMatch(q -> isAnswered(answers, q));
    }

    /**
     * 간편 진단 응답으로 원점수·대표 유형·판정 근거를 산출한다.
     *
     * @param answers 문항 코드({@code q1}~{@code q3}) → 선택지 코드({@code A}~{@code D}). 부분 응답 허용
     * @return Q1~Q3 이 모두 응답됐을 때만 산출 결과, 하나라도 미응답이면 {@link Optional#empty()} (=미측정)
     * @throws IllegalArgumentException 응답 중 허용 선택지가 아닌 값이 있는 경우
     */
    public Optional<RiskAssessment> assess(Map<String, String> answers) {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (String question : SIMPLE_QUESTIONS) {
            if (isAnswered(answers, question)) {
                points.put(question, points(answers.get(question)));
            }
        }
        if (points.size() < SIMPLE_QUESTIONS.size()) {
            return Optional.empty();
        }

        int score = points.values().stream().mapToInt(Integer::intValue).sum();
        String grade = classify(score);
        return Optional.of(new RiskAssessment(
                score,
                grade,
                LABEL_BY_GRADE.get(grade),
                THRESHOLD_BY_GRADE.get(grade),
                SAFE_RATIO_ADJUST_BY_GRADE.get(grade),
                mixedResponseNote(points.values()),
                rationale(answers, points)));
    }

    private List<RiskRationale> rationale(Map<String, String> answers, Map<String, Integer> points) {
        List<RiskRationale> rationale = new ArrayList<>();
        points.forEach((question, point) -> {
            String choice = normalize(answers.get(question));
            rationale.add(new RiskRationale(
                    question, choice, point, READING_BY_ANSWER.get(question + ":" + choice)));
        });
        return rationale;
    }

    private String mixedResponseNote(java.util.Collection<Integer> points) {
        int max = points.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int min = points.stream().mapToInt(Integer::intValue).min().orElseThrow();
        return (max - min) >= MIXED_RESPONSE_SPREAD ? MIXED_RESPONSE_NOTE : null;
    }

    private String classify(int score) {
        if (score <= STABLE_MAX_SCORE) {
            return STABLE;
        }
        if (score <= BALANCED_MAX_SCORE) {
            return BALANCED;
        }
        if (score <= AGGRESSIVE_MAX_SCORE) {
            return AGGRESSIVE;
        }
        return CHALLENGING;
    }

    private boolean isAnswered(Map<String, String> answers, String question) {
        if (answers == null) {
            return false;
        }
        String choice = answers.get(question);
        return choice != null && !choice.isBlank();
    }

    private String normalize(String choice) {
        return choice == null ? null : choice.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
