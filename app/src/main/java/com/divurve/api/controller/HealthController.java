package com.divurve.api.controller;

import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 liveness 확인용 컨트롤러. DB 접근 없이 프로세스가 살아있는지만 확인한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "서버 liveness 확인용 엔드포인트")
public class HealthController {

    @Operation(summary = "기본 연결 확인 (DB 미접근)", description = "서버가 살아있는지 확인하는 liveness ping")
    @GetMapping("/health/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.of(Map.of("status", "ok"));
    }
}
