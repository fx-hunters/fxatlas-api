package com.divurve.api.dto.goal;

import java.util.List;

/**
 * 목표 목록 응답 (GET /goals, API 명세 v2 §3).
 *
 * <p><b>{@code route_enabled} 필드를 제거했다</b> (이슈 #84). 그 값은 "Route 계산 로직이
 * 미확정이라 목표 기능이 막혀 있다"는 신호였고, 프론트는 이를 보고 목표 빈 상태를 그렸다.
 * 플래너 명세가 계산 정책을 확정해 기능이 항상 열리면서 항상 {@code true} 인 죽은 필드가 되므로
 * 남기지 않는다. 빈 목록은 이제 "아직 목표를 만들지 않았다"는 뜻이며, 프론트는 새 목표 만들기를
 * 안내한다(플래너 명세 §20).
 *
 * @param goals 목표 목록
 */
public record GoalListResponse(List<GoalResponse> goals) {
}
