package com.divurve.api.dto.plan;

/**
 * 회차 건너뛰기 응답 (POST /plans/{id}/steps/{seq}/skip, 명세 3.7).
 * 연속 건너뛰기가 임계치에 도달하면 {@code safeModeTriggered=true} 로 새 계획이 safe_mode 로 생성된다 (FR-SF-01).
 */
public record StepSkipResponse(
        Redistributed redistributed,
        AchieveProb achieveProb,
        int consecutiveSkips,
        boolean safeModeTriggered,
        int newPlanVersion) {

    /** 남은 회차 재분배. */
    public record Redistributed(
            double perStepBefore,
            double perStepAfter,
            double increasePct) {
    }

    /** 달성 확률 변화. */
    public record AchieveProb(double before, double after) {
    }
}
