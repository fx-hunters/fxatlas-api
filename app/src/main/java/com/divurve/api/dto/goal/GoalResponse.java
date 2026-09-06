package com.divurve.api.dto.goal;

import java.time.LocalDate;

/**
 * 목표 상세/생성 응답 (GET /goals/{id}, POST /goals, API 명세 v2 §3).
 * {@code heldAmount} 는 서버가 /deposits 에서 조회해 채운다.
 *
 * <p>v1 의 {@code suggested}(권장 안전비율·하한·분할 회차) 블록은 <b>제거했다</b>. 세 값 모두
 * 요구사항 v2 §4.12 에서 미확정으로 선언된 Route 계산 결과이고(50/70/85/95% 와 4~8회는 후보일 뿐
 * 확정 요구사항이 아니다), 명세 v2 §6.3 은 "안전·기회 버킷 비율 · 목적별 최소 안전 비율 ·
 * 권장 분할 회차"를 확정 전까지 명세하지 않는다고 못박는다. 확정되지 않은 수치를 응답에 실을 수 없다.
 */
public record GoalResponse(
        String id,
        String name,
        String kind,
        String purpose,
        String currencyCode,
        double targetAmount,
        LocalDate targetDate,
        String recurInterval,
        long budgetAmount,
        String budgetCurrencyCode,
        String budgetPeriod,
        boolean isSpeculative,
        String status,
        double heldAmount) {
}
