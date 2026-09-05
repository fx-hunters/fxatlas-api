package com.divurve.api.controller;

import com.divurve.api.dto.ConnectivityCheckRequest;
import com.divurve.api.dto.ConnectivityCheckResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.connectivity.ConnectivityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트·DB 연동 확인용 테스트 컨트롤러. 실제 비즈니스 도메인이 아니라
 * "요청 → 서비스 → DB 왕복 → data/meta 응답"이 전 구간 동작하는지 검증하기 위한 엔드포인트다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Connectivity Check", description = "프론트·DB 연동 테스트용 엔드포인트")
public class ConnectivityCheckController {

    private final ConnectivityCheckService service;

    public ConnectivityCheckController(ConnectivityCheckService service) {
        this.service = service;
    }

    @Operation(summary = "기본 연결 확인 (DB 미접근)", description = "서버가 살아있는지 확인하는 liveness ping")
    @GetMapping("/health/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.of(Map.of("status", "ok"));
    }

    @Operation(summary = "테스트 행 생성", description = "message 를 받아 DB 에 저장하고 생성된 행을 반환한다")
    @PostMapping("/connectivity-checks")
    public ApiResponse<ConnectivityCheckResponse> create(@RequestBody ConnectivityCheckRequest request) {
        return ApiResponse.of(ConnectivityCheckResponse.from(service.create(request.message())));
    }

    @Operation(summary = "테스트 행 전체 조회", description = "저장된 모든 테스트 행을 반환한다")
    @GetMapping("/connectivity-checks")
    public ApiResponse<List<ConnectivityCheckResponse>> findAll() {
        return ApiResponse.of(service.findAll().stream().map(ConnectivityCheckResponse::from).toList());
    }
}
