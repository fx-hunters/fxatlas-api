package com.divurve.domain.goal;

/**
 * 목표 유형 상수 (플래너 명세 §4).
 *
 * <p>둘의 차이는 Curve 의 마지막 지점이다 — 마감형은 <b>목표 도착</b>, 정기형은 <b>다음 점검</b>이다.
 * 정기형은 목표일을 필수로 받지 않으며, 점검 기간이 끝나면 계획을 다시 검토한다(명세 §10.3).
 *
 * <p>해외 ETF 목표도 Divurve 가 다루는 범위는 <b>매수하기 위한 외화 준비 계획까지</b>다 —
 * 종목 주문이나 매수 시점 추천은 포함하지 않는다(명세 §4).
 */
public final class GoalType {

    /** 마감형 — 특정 날짜까지 목표 외화를 준비 (여행·학비·해외 결제). */
    public static final String DEADLINE = "deadline";

    /** 정기형 — 정해진 주기로 외화 자금을 준비 (해외 ETF 투자자금·외화예금·정기 환전). */
    public static final String RECURRING = "recurring";

    private GoalType() {
    }
}
