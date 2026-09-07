package com.divurve.api.controller;

import static java.util.Objects.requireNonNull;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.route.RouteContextResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.route.RouteContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route 연결 엔드포인트 (API 명세 v2 §6) — <b>우선순위 P(구조만 준비)</b>.
 *
 * <p>이 컨트롤러는 데이터 계약인 RouteContext 직렬화 하나만 제공하며 어떤 계획도 계산하지
 * 않는다. 계획 계산은 {@code PlanController} 가 담당한다. RouteContext 의 값을 실제로 채우는
 * 것은 이슈 #85 다 — 지금은 기준 시각을 뺀 모든 값이 비어 있다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/route")
@Tag(name = "Route", description = "Route 연결 — 계산 없이 데이터 계약만 직렬화")
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
     *
     * <p>기준 환율은 계획 계산이 쓰는 것과 <b>같은 전제</b>다 — 화면이 보는 환율과 계획이 쓴 환율이
     * 다르면 사용자가 수치를 대조할 수 없다 (불변조건 §21-13).
     */
    @Operation(
            summary = "RouteContext 조회",
            description = "진단·자산·기준환율·스트레스 결과를 전달하는 데이터 계약만 직렬화하며 어떤 계획도 "
                    + "계산하지 않는다(명세 v2 §6.1). 블록 하나를 채우지 못해도 나머지는 그대로 낸다 — "
                    + "값을 지어내지 않고 비운다(플래너 명세 §20). model_path·forecast_factors 는 "
                    + "FR-FC-12 에 따라 계약에서 제외한다.")
    @GetMapping("/context")
    public ApiResponse<RouteContextResponse> getRouteContext(@CurrentUser UUID userId) {
        return ApiResponse.of(RouteContextResponse.from(routeContextService.getContext(userId)));
    }
}
