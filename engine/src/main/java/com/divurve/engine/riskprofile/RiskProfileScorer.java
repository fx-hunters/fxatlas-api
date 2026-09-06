package com.divurve.engine.riskprofile;

import com.divurve.engine.EngineComponent;
import java.util.List;

/**
 * 투자성향 등급 산출기 (이슈 #10, FR-ON-02, ERD v3.0). 간편 진단 Q1~Q3 응답값(각 0~3, A=0·B=1·C=2·D=3)의
 * <b>합계</b>로 원점수(0~9)와 대표 등급 4종을 결정론적으로 계산한다.
 *
 * <ul>
 *   <li>합계 0~2 → {@code stable}(안정항로형)</li>
 *   <li>합계 3~4 → {@code balanced}(균형항로형)</li>
 *   <li>합계 5~6 → {@code aggressive}(적극항로형)</li>
 *   <li>합계 7~9 → {@code challenging}(도전항로형)</li>
 * </ul>
 *
 * <p>이 클래스는 순수 계산만 한다 — Spring/JPA 의존이 없고 부작용이 없다.
 */
@EngineComponent
public class RiskProfileScorer {

    /** 문항당 최소 선택값 (A=0). */
    public static final int MIN_CHOICE = 0;
    /** 문항당 최대 선택값 (D=3). */
    public static final int MAX_CHOICE = 3;
    /** 최대 문항 수 (간편 진단 Q1~Q3). */
    public static final int MAX_QUESTIONS = 3;

    /** 안정 등급 상한(합계). */
    public static final int STABLE_MAX_SCORE = 2;
    /** 균형 등급 상한(합계). */
    public static final int BALANCED_MAX_SCORE = 4;
    /** 적극 등급 상한(합계). */
    public static final int AGGRESSIVE_MAX_SCORE = 6;

    /** 안정항로형 등급 코드. */
    public static final String STABLE = "stable";
    /** 균형항로형 등급 코드. */
    public static final String BALANCED = "balanced";
    /** 적극항로형 등급 코드. */
    public static final String AGGRESSIVE = "aggressive";
    /** 도전항로형 등급 코드. */
    public static final String CHALLENGING = "challenging";

    /**
     * 문항 응답값 목록으로 원점수와 등급을 산출한다.
     *
     * @param choices 문항별 선택값(각 {@value #MIN_CHOICE}~{@value #MAX_CHOICE}), 1개 이상 {@value #MAX_QUESTIONS}개 이하
     * @throws IllegalArgumentException 응답이 비었거나, 문항 수가 초과거나, 선택값이 허용 범위를 벗어난 경우
     */
    public RiskAssessment assess(List<Integer> choices) {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("성향 진단 응답이 비어 있습니다.");
        }
        if (choices.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("성향 진단은 최대 " + MAX_QUESTIONS + "문항입니다 (입력 " + choices.size() + "문항).");
        }
        int score = 0;
        for (Integer choice : choices) {
            if (choice == null || choice < MIN_CHOICE || choice > MAX_CHOICE) {
                throw new IllegalArgumentException(
                        "선택값은 " + MIN_CHOICE + "~" + MAX_CHOICE + " 여야 합니다 (입력 " + choice + ").");
            }
            score += choice;
        }
        return new RiskAssessment(score, classify(score));
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
}
