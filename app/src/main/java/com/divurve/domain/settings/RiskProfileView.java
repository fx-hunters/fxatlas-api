package com.divurve.domain.settings;

import java.util.List;

/**
 * 성향 프로필 조회 결과 (이슈 #10). 트랜잭션 안에서 엔티티를 평탄화해 만든 도메인→웹 전달용 값이다.
 * 엔티티/영속성 컬렉션을 컨트롤러로 노출하지 않기 위한 경계 객체다.
 *
 * @param riskType 등급 코드 (stable·balanced·flexible)
 * @param score    원점수
 * @param answers  진단 응답 이력
 */
public record RiskProfileView(String riskType, int score, List<Answer> answers) {

    /** 문항별 응답. */
    public record Answer(String questionCode, int choice) {
    }
}
