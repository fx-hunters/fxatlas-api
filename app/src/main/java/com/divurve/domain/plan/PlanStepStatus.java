package com.divurve.domain.plan;

/**
 * 계획 회차의 상태 상수 (플래너 명세 §13.2).
 *
 * <pre>
 * SCHEDULED → DUE → COMPLETED
 *                 ↘ SKIPPED
 * </pre>
 *
 * <p>이전의 {@code PENDING} 은 {@link #SCHEDULED} 로 바뀌었다 — 명세 §13.2 가 "예정"과
 * "도래"를 구분하기 때문이다. 둘 다 아직 실행되지 않은 상태이므로 완료·건너뛰기의 출발점이며,
 * 그 판정은 {@code PlanStep#isOpen()} 이 담당한다.
 */
public final class PlanStepStatus {

    /** 예정된 회차. 아직 예정일이 오지 않았다. */
    public static final String SCHEDULED = "scheduled";

    /** 예정일이 도래한 회차. 지금 확인하거나 기록해야 할 "다음 행동"의 후보다 (명세 §11.3). */
    public static final String DUE = "due";

    /** 정상적으로 완료된 상태. */
    public static final String COMPLETED = "completed";

    /** 건너뛴 상태. 남은 금액은 남은 회차로 재분배된다 (명세 §15). */
    public static final String SKIPPED = "skipped";

    private PlanStepStatus() {
    }
}
