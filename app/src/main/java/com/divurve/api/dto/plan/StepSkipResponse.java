package com.divurve.api.dto.plan;

/**
 * 회차 건너뛰기 응답 (POST /plans/{id}/steps/{seq}/skip) — <b>우선순위 P(구조만 준비)</b>.
 *
 * <p>v1 의 {@code safeModeTriggered} 필드는 <b>제거했다</b>. v1 안전모드(연속 건너뛰기 3회 도달 시
 * 계획을 safe_mode 로 재생성)는 이미 삭제된 기능이고, 임계치 3 자체가 요구사항 v2 §4.12 의 미확정
 * 값이었다. {@code achieveProb} 는 달성 확률의 정의가 미확정이라 0 으로 고정되어 있다(명세 v2 §6.3).
 */
public record StepSkipResponse(
        Redistributed redistributed,
        AchieveProb achieveProb,
        int consecutiveSkips,
        int newPlanVersion) {

    /** 남은 회차 재분배. */
    public record Redistributed(
            double perStepBefore,
            double perStepAfter,
            double increasePct) {
    }

    /** 달성 확률 변화. 정의 미확정(요구사항 v2 §4.12)이라 현재는 항상 0 이다. */
    public record AchieveProb(double before, double after) {
    }
}
