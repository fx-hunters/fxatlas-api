package com.divurve.api.dto.goal;

import java.util.List;

/**
 * 목표 목록 응답 (GET /goals, API 명세 v2 §3·§6.2) — <b>우선순위 P(구조만 준비)</b>.
 *
 * <p>Route 계산 로직이 요구사항 v2 §4.12 에서 미확정이므로, 기능 플래그({@code route.enabled})가
 * 꺼진 동안에는 <b>항상 빈 배열과 {@code route_enabled=false}</b> 를 돌려준다. 프론트는 이 값으로
 * 목표 빈 상태(화면 v2 §18)를 그린다.
 *
 * @param goals        목표 목록. 플래그가 꺼져 있으면 항상 비어 있다
 * @param routeEnabled Route 기능 플래그 상태
 */
public record GoalListResponse(List<GoalResponse> goals, boolean routeEnabled) {

    /** Route 가 꺼져 있을 때의 빈 상태 응답. */
    public static GoalListResponse empty() {
        return new GoalListResponse(List.of(), false);
    }
}
