package com.divurve.engine.planner;

/**
 * 환율 범위에 따른 예상 원화 비용 (플래너 명세 §9.3).
 *
 * <p>비용은 환율에 <b>비례</b>하므로 {@code lowKrw} 는 환율 하단, {@code highKrw} 는 환율
 * 상단에서 나온다. 정기형의 확보 외화는 반대 방향이므로 혼동하지 말 것 —
 * {@link RecurringAcquisitionCalculator} 참고.
 *
 * @param lowKrw  환율 하단 기준 비용
 * @param baseKrw 기준 환율 비용
 * @param highKrw 환율 상단 기준 비용
 */
public record CostRange(long lowKrw, long baseKrw, long highKrw) {
}
