package com.divurve.engine.riskprofile;

import com.divurve.engine.EngineComponent;
import java.util.List;

/**
 * 투자성향 등급 산출기 (이슈 #10, FR-ON-02). 5문항 이내의 문항 응답값(각 1~3)으로부터
 * 원점수와 등급(안정·균형·유연)을 결정론적으로 계산한다.
 *
 * <p>등급은 <b>응답 개수에 무관하게</b> 평균 선택값으로 판정한다 — "5문항 이내"라 문항 수가 가변이므로
 * 합계 임계값 대신 1문항당 평균을 쓴다(1에 가까울수록 보수적, 3에 가까울수록 공격적).
 * <ul>
 *   <li>평균 ≤ {@value #STABLE_MAX_AVG} → {@code stable}(안정)</li>
 *   <li>평균 ≤ {@value #BALANCED_MAX_AVG} → {@code balanced}(균형)</li>
 *   <li>그 외 → {@code flexible}(유연)</li>
 * </ul>
 * 경계는 [1,3] 구간 삼등분(1⅔·2⅓)이다.
 *
 * <p>이 클래스는 순수 계산만 한다 — Spring/JPA 의존이 없고 부작용이 없다.
 */
@EngineComponent
public class RiskProfileScorer {

    /** 문항당 최소 선택값. */
    public static final int MIN_CHOICE = 1;
    /** 문항당 최대 선택값. */
    public static final int MAX_CHOICE = 3;
    /** 최대 문항 수 (FR-ON-02: 5문항 이내). */
    public static final int MAX_QUESTIONS = 5;

    /** 안정 등급 상한(평균) — 1⅔. */
    public static final double STABLE_MAX_AVG = 5.0 / 3.0;
    /** 균형 등급 상한(평균) — 2⅓. */
    public static final double BALANCED_MAX_AVG = 7.0 / 3.0;

    /** 안정 등급 코드. */
    public static final String STABLE = "stable";
    /** 균형 등급 코드. */
    public static final String BALANCED = "balanced";
    /** 유연 등급 코드. */
    public static final String FLEXIBLE = "flexible";

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
        double average = (double) score / choices.size();
        return new RiskAssessment(score, classify(average));
    }

    private String classify(double average) {
        if (average <= STABLE_MAX_AVG) {
            return STABLE;
        }
        if (average <= BALANCED_MAX_AVG) {
            return BALANCED;
        }
        return FLEXIBLE;
    }
}
