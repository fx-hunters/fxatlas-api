package com.divurve.domain.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 성향 진단 문항별 응답 (이슈 #10). {@link RiskProfile} 의 값 컬렉션으로,
 * {@code risk_profile_answers} 테이블에 한 행씩 저장된다. 표시/재진단 이력 참고용이며 판정값은 아니다.
 */
@Embeddable
public class RiskAnswer {

    @Column(name = "question_code", nullable = false)
    private String questionCode;

    @Column(name = "choice", nullable = false)
    private int choice;

    /** JPA 전용 기본 생성자. */
    protected RiskAnswer() {
    }

    private RiskAnswer(String questionCode, int choice) {
        this.questionCode = questionCode;
        this.choice = choice;
    }

    /** 문항 코드와 선택값으로 응답을 만든다. */
    public static RiskAnswer of(String questionCode, int choice) {
        return new RiskAnswer(questionCode, choice);
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public int getChoice() {
        return choice;
    }
}
