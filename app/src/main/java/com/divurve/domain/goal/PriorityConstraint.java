package com.divurve.domain.goal;

/**
 * 상황 변화 시 우선 유지할 조건 (플래너 명세 §5.1·§17).
 *
 * <p>시나리오 재계산은 이 값을 기준으로 수행한다. <b>시스템은 사용자의 우선 조건을 알리지 않고
 * 임의로 바꾸지 않는다</b>(명세 §17).
 *
 * <p>기본값은 목표 유형에 따라 다르다 — 마감형은 금액+날짜(명세 §5.2), 정기형은 예산(§5.3).
 */
public final class PriorityConstraint {

    /** 목표 외화 금액을 유지한다. 필요한 원화나 목표 날짜의 변경 가능성을 보여준다. */
    public static final String AMOUNT = "amount";

    /** 목표 날짜를 유지한다. 회차별 외화·원화나 예산의 변경 가능성을 보여준다. */
    public static final String DATE = "date";

    /** 사용자가 정한 원화 예산을 넘기지 않는다. 확보 외화 범위나 목표 날짜의 변화를 보여준다. */
    public static final String BUDGET = "budget";

    private PriorityConstraint() {
    }
}
