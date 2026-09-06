package com.divurve.api.controller;

import static java.util.Objects.requireNonNull;

import com.divurve.api.dto.route.RouteContextResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.route.RouteContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route 연결 엔드포인트 (API 명세 v2 §6) — <b>우선순위 P(구조만 준비)</b>.
 *
 * <p>요구사항 v2 §4.12 는 Route 의 목적함수 · 버킷 비율 · 분할 회차 · 달성 확률 정의를 전부
 * 미확정으로 둔다. 그래서 <b>계산하는 엔드포인트는 명세되지 않았고</b>, 이 컨트롤러는 데이터 계약인
 * RouteContext 직렬화 하나만 제공한다. 계획 계산은 {@code PlanController} 가 501 로 막고 있다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/route")
@Tag(name = "Route", description = "Route 연결 (P — 구조만 준비)")
public class RouteController {

    private final RouteContextService routeContextService;

    public RouteController(RouteContextService routeContextService) {
        this.routeContextService = requireNonNull(routeContextService, "routeContextService");
    }

    /**
     * RouteContext 직렬화. 계산은 하지 않는다 (FR-RT-01, 명세 v2 §6.1).
     *
     * <p>{@code model_path} 와 {@code forecast_factors} 는 계약에서 의도적으로 제외한다 — 방향 전망을
     * Route 계산 입력으로 전달하지 않는다는 FR-FC-12 를 API 계약 수준에서 강제한다.
     */
    @Operation(
            summary = "RouteContext 조회 (P)",
            description = "우선순위 P(구조만 준비). 진단·자산·기준환율·스트레스 결과를 전달하는 데이터 계약만 "
                    + "직렬화하며 어떤 계획도 계산하지 않는다(명세 v2 §6.1). Route 계산 로직은 요구사항 v2 "
                    + "§4.12 에서 미확정이므로 값은 확정 전까지 비어 있다. model_path·forecast_factors 는 "
                    + "FR-FC-12 에 따라 계약에서 제외한다.")
    @GetMapping("/context")
    public ApiResponse<RouteContextResponse> getRouteContext() {
        return ApiResponse.of(RouteContextResponse.from(routeContextService.getContext()));
    }
}
