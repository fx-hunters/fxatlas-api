package com.divurve.api.controller;

import static java.util.Objects.requireNonNull;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.goal.GoalCreateRequest;
import com.divurve.api.dto.goal.GoalListResponse;
import com.divurve.api.dto.goal.GoalResponse;
import com.divurve.api.dto.goal.GoalUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.GoalService;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.route.RouteFeatureFlag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목표 엔드포인트 (API 명세 v2 §3·§6.2) — <b>우선순위 P(구조만 준비)</b>.
 * 소유자 기준 필터를 적용한다 (NFR-SE-03).
 *
 * <p><b>Route 강등</b> — 목표 생성·수정·삭제는 Route 계산(계획 수립)의 입구다. 요구사항 v2 §4.12 가
 * Route 를 전부 P 로 두었으므로 {@link RouteFeatureFlag} 가 꺼져 있으면 쓰기 3종은 501 로 막고,
 * {@code GET /goals} 는 빈 배열과 {@code route_enabled=false} 를 돌려준다(명세 §3·§6.2).
 * {@code GET /goals/{id}} 는 계산이 아닌 단순 조회라 막지 않는다 — 플래그를 켜서 만든 목표를
 * 그대로 읽을 수 있어야 하고, 플래그가 꺼진 동안에는 생성 자체가 불가능해 노출 위험이 없다.
 *
 * <p>소유자는 {@link CurrentUser} 로 주입받는다. 이슈 #50 이전에는 하드코딩된 UUID 상수를
 * 5개 메서드가 공유해, <b>모든 사용자가 같은 목표 목록을 보고 서로의 목표를 수정·삭제할 수 있었다</b>.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goal", description = "목표 CRUD (P — Route 확정 전까지 쓰기는 501)")
public class GoalController {

    private final GoalService goalService;
    private final RouteFeatureFlag routeFeatureFlag;

    public GoalController(GoalService goalService, RouteFeatureFlag routeFeatureFlag) {
        this.goalService = requireNonNull(goalService, "goalService");
        this.routeFeatureFlag = requireNonNull(routeFeatureFlag, "routeFeatureFlag");
    }

    @Operation(
            summary = "목표 목록 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12)이므로 "
                    + "기능 플래그(route.enabled)가 꺼진 동안에는 항상 빈 배열과 route_enabled=false 를 "
                    + "반환한다(명세 v2 §6.2).")
    @GetMapping
    public ApiResponse<GoalListResponse> listGoals(@CurrentUser UUID userId) {
        if (!routeFeatureFlag.isEnabled()) {
            return ApiResponse.of(GoalListResponse.empty());
        }
        List<Goal> goals = goalService.listByOwner(userId);
        List<GoalResponse> responses = goals.stream()
                .map(goal -> toGoalResponse(userId, goal))
                .toList();
        return ApiResponse.of(new GoalListResponse(responses, true));
    }

    @Operation(
            summary = "목표 생성 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PostMapping
    public ApiResponse<GoalResponse> createGoal(
            @CurrentUser UUID userId,
            @RequestBody GoalCreateRequest request) {
        routeFeatureFlag.requireEnabled();
        Goal goal = goalService.create(
                userId,
                request.name(),
                request.kind(),
                request.purpose(),
                request.currencyCode(),
                request.targetAmount(),
                request.targetDate(),
                request.recurInterval(),
                request.budgetAmount(),
                request.budgetCurrencyCode(),
                request.budgetPeriod(),
                request.isSpeculative());
        return ApiResponse.of(toGoalResponse(userId, goal));
    }

    @Operation(summary = "목표 상세")
    @GetMapping("/{id}")
    public ApiResponse<GoalResponse> getGoal(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        UUID goalId = UUID.fromString(id);
        Goal goal = goalService.getByIdAndOwner(userId, goalId);
        return ApiResponse.of(toGoalResponse(userId, goal));
    }

    @Operation(
            summary = "목표 수정 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @PutMapping("/{id}")
    public ApiResponse<GoalResponse> updateGoal(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody GoalUpdateRequest request) {
        routeFeatureFlag.requireEnabled();
        UUID goalId = UUID.fromString(id);
        Goal goal = goalService.update(
                userId,
                goalId,
                request.name(),
                request.targetAmount(),
                request.targetDate(),
                request.budgetAmount(),
                request.budgetPeriod(),
                request.isSpeculative());
        return ApiResponse.of(toGoalResponse(userId, goal));
    }

    @Operation(
            summary = "목표 삭제 (P)",
            description = "우선순위 P(구조만 준비). Route 계산 로직 미확정(요구사항 v2 §4.12) — 구조만 준비. "
                    + "기능 플래그(route.enabled)가 꺼져 있으면 501 을 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "501", description = "route.enabled=false — Route 계산 로직 미확정")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGoal(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        routeFeatureFlag.requireEnabled();
        UUID goalId = UUID.fromString(id);
        goalService.delete(userId, goalId);
        return ApiResponse.of(null);
    }

    private GoalResponse toGoalResponse(UUID userId, Goal goal) {
        double heldAmount = goalService.getHeldAmountByCurrency(userId, goal.getCurrencyCode());
        return new GoalResponse(
                goal.getId().toString(),
                goal.getName(),
                goal.getKind(),
                goal.getPurpose(),
                goal.getCurrencyCode(),
                goal.getTargetAmount(),
                goal.getTargetDate(),
                goal.getRecurInterval(),
                goal.getBudgetAmount(),
                goal.getBudgetCurrencyCode(),
                goal.getBudgetPeriod(),
                goal.isSpeculative(),
                goal.getStatus(),
                heldAmount);
    }
}
