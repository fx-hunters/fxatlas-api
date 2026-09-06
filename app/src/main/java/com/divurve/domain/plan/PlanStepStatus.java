package com.divurve.domain.plan;

/**
 * 계획 회차의 상태 상수.
 */
public final class PlanStepStatus {

    /** 아직 실행되지 않은 상태. */
    public static final String PENDING = "pending";

    /** 정상적으로 완료된 상태. */
    public static final String COMPLETED = "completed";

    /** 건너뛴 상태 (다음 회차로 부담이 넘어감). */
    public static final String SKIPPED = "skipped";

    private PlanStepStatus() {
    }
}
