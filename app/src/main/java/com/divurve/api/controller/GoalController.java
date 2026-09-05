package com.divurve.api.controller;

import com.divurve.api.dto.goal.GoalCreateRequest;
import com.divurve.api.dto.goal.GoalListResponse;
import com.divurve.api.dto.goal.GoalResponse;
import com.divurve.api.dto.goal.GoalUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목표 엔드포인트 스텁 (명세 2·3.2장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goal", description = "목표 CRUD")
public class GoalController {

    @Operation(summary = "목표 목록")
    @GetMapping
    public ApiResponse<GoalListResponse> listGoals() {
        throw new NotImplementedException();
    }

    @Operation(summary = "목표 생성")
    @PostMapping
    public ApiResponse<GoalResponse> createGoal(@RequestBody GoalCreateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "목표 상세")
    @GetMapping("/{id}")
    public ApiResponse<GoalResponse> getGoal(@PathVariable String id) {
        throw new NotImplementedException();
    }

    @Operation(summary = "목표 수정")
    @PutMapping("/{id}")
    public ApiResponse<GoalResponse> updateGoal(
            @PathVariable String id,
            @RequestBody GoalUpdateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "목표 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGoal(@PathVariable String id) {
        throw new NotImplementedException();
    }
}
