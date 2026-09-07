package com.divurve.domain.plan;

/**
 * 계획 상태 상수 (플래너 명세 §13.1).
 *
 * <p>여섯 상태를 {@code is_active} boolean 하나로 표현할 수 없어 별도 컬럼으로 둔다.
 * 목표당 {@link #ACTIVE} 계획은 하나뿐이며, 이는 애플리케이션 코드가 아니라
 * {@code uq_plans_active_per_goal} 부분 유니크 인덱스가 보장한다(명세 §21-9·10).
 *
 * <p>명세 §13.1 은 상태를 대문자로 적었지만 저장 값은 <b>소문자</b>다 — 기존
 * {@link PlanStepStatus} 가 소문자 문자열 상수를 쓰고 있어 어휘를 통일했다.
 */
public final class PlanStatus {

    /** 계산됐지만 아직 적용하지 않은 계획. 시나리오 미리보기가 여기에 머문다 (명세 §18). */
    public static final String DRAFT = "draft";

    /** 현재 적용 중인 계획. 목표당 하나뿐이다. */
    public static final String ACTIVE = "active";

    /** 조건 변화로 재검토가 필요한 계획 (명세 §13.2). */
    public static final String NEEDS_REVIEW = "needs_review";

    /** 마감형 목표 완료. */
    public static final String COMPLETED = "completed";

    /** 사용자에 의해 일시 정지된 계획 (명세 §15 조정 선택지). */
    public static final String PAUSED = "paused";

    /** 새 버전 적용으로 대체된 과거 계획. 완료 회차 기록은 새 버전에도 보존된다 (명세 §21-11). */
    public static final String SUPERSEDED = "superseded";

    private PlanStatus() {
    }
}
