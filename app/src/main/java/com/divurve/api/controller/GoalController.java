package com.divurve.api.controller;

import com.divurve.api.dto.goal.GoalCreateRequest;
import com.divurve.api.dto.goal.GoalListResponse;
import com.divurve.api.dto.goal.GoalResponse;
import com.divurve.api.dto.goal.GoalUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.goal.GoalService;
import com.divurve.domain.goal.entity.Goal;
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
 * 목표 엔드포인트 (명세 2·3.2장).
 * 소유자 기준 필터를 적용한다 (NFR-SE-03). held_amount는 요청시 자동 조회된다 (FR-RT-05).
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goal", description = "목표 CRUD")
public class GoalController {

    private final GoalService goalService;
    private static final UUID CURRENT_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @Operation(summary = "목표 목록")
    @GetMapping
    public ApiResponse<GoalListResponse> listGoals() {
        List<Goal> goals = goalService.listByOwner(CURRENT_USER_ID);
        List<GoalResponse> responses = goals.stream()
                .map(this::toGoalResponse)
                .toList();
        return ApiResponse.of(new GoalListResponse(responses));
    }

    @Operation(summary = "목표 생성")
    @PostMapping
    public ApiResponse<GoalResponse> createGoal(@RequestBody GoalCreateRequest request) {
        Goal goal = goalService.create(
                CURRENT_USER_ID,
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
        return ApiResponse.of(toGoalResponse(goal));
    }

    @Operation(summary = "목표 상세")
    @GetMapping("/{id}")
    public ApiResponse<GoalResponse> getGoal(@PathVariable String id) {
        UUID goalId = UUID.fromString(id);
        Goal goal = goalService.getByIdAndOwner(CURRENT_USER_ID, goalId);
        return ApiResponse.of(toGoalResponse(goal));
    }

    @Operation(summary = "목표 수정")
    @PutMapping("/{id}")
    public ApiResponse<GoalResponse> updateGoal(
            @PathVariable String id,
            @RequestBody GoalUpdateRequest request) {
        UUID goalId = UUID.fromString(id);
        Goal goal = goalService.update(
                CURRENT_USER_ID,
                goalId,
                request.name(),
                request.targetAmount(),
                request.targetDate(),
                request.budgetAmount(),
                request.budgetPeriod(),
                request.isSpeculative());
        return ApiResponse.of(toGoalResponse(goal));
    }

    @Operation(summary = "목표 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGoal(@PathVariable String id) {
        UUID goalId = UUID.fromString(id);
        goalService.delete(CURRENT_USER_ID, goalId);
        return ApiResponse.of(null);
    }

    private GoalResponse toGoalResponse(Goal goal) {
        double heldAmount = goalService.getHeldAmountByCurrency(CURRENT_USER_ID, goal.getCurrencyCode());
        GoalResponse.Suggested suggested = new GoalResponse.Suggested(0.0, 0.0, 0);
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
                heldAmount,
                suggested);
    }
}
