package com.divurve.api.controller;

import com.divurve.api.dto.plan.ActivePlanResponse;
import com.divurve.api.dto.plan.PlanCreateRequest;
import com.divurve.api.dto.plan.PlanPreviewRequest;
import com.divurve.api.dto.plan.PlanPreviewResponse;
import com.divurve.api.dto.plan.PlanResponse;
import com.divurve.api.dto.plan.PlanVersionListResponse;
import com.divurve.api.dto.plan.StepCompleteRequest;
import com.divurve.api.dto.plan.StepCompleteResponse;
import com.divurve.api.dto.plan.StepSkipResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계획 엔드포인트 스텁 (명세 2·3.1·3.7장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 * 경로가 /plans 와 /goals/{id}/plans 를 넘나들어 base 는 /api/v1 로 둔다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Plan", description = "계획 미리보기·확정·회차 실행")
public class PlanController {

    @Operation(summary = "계획 미리보기 (저장하지 않는다)")
    @PostMapping("/plans/preview")
    public ApiResponse<PlanPreviewResponse> preview(@RequestBody PlanPreviewRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "계획 확정·저장")
    @PostMapping("/goals/{id}/plans")
    public ApiResponse<PlanResponse> createPlan(
            @PathVariable String id,
            @RequestBody PlanCreateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "계획 버전 이력")
    @GetMapping("/goals/{id}/plans")
    public ApiResponse<PlanVersionListResponse> listPlanVersions(@PathVariable String id) {
        throw new NotImplementedException();
    }

    @Operation(summary = "활성 계획과 회차")
    @GetMapping("/goals/{id}/plans/active")
    public ApiResponse<ActivePlanResponse> getActivePlan(@PathVariable String id) {
        throw new NotImplementedException();
    }

    @Operation(summary = "회차 완료 기록")
    @PostMapping("/plans/{id}/steps/{seq}/complete")
    public ApiResponse<StepCompleteResponse> completeStep(
            @PathVariable String id,
            @PathVariable int seq,
            @RequestBody StepCompleteRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "회차 건너뛰기")
    @PostMapping("/plans/{id}/steps/{seq}/skip")
    public ApiResponse<StepSkipResponse> skipStep(
            @PathVariable String id,
            @PathVariable int seq) {
        throw new NotImplementedException();
    }
}
