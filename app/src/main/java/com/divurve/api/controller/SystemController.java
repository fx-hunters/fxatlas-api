package com.divurve.api.controller;

import com.divurve.api.dto.system.HomeSummaryResponse;
import com.divurve.api.dto.system.NotificationListResponse;
import com.divurve.api.dto.system.SafeModeResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.system.SafeModeService;
import com.divurve.engine.safemode.SafeModeCheckResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기타(홈 요약·안전모드·알림 목록) 엔드포인트 (명세 2·3.9장).
 * 안전모드 조회는 구현되었고, 홈 요약과 알림 목록은 미구현이다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "홈 요약·안전모드·알림 목록")
public class SystemController {

    private final SafeModeService safeModeService;

    public SystemController(SafeModeService safeModeService) {
        this.safeModeService = safeModeService;
    }

    @Operation(summary = "홈 3블록 통합 조회")
    @GetMapping("/home/summary")
    public ApiResponse<HomeSummaryResponse> getHomeSummary() {
        throw new NotImplementedException();
    }

    @Operation(summary = "안전모드 상태 (명세 3.9, FR-SF-01~05)")
    @GetMapping("/system/safe-mode")
    public ApiResponse<SafeModeResponse> getSafeMode(AuthPrincipal principal) {
        UUID userId = principal.userId();
        SafeModeCheckResult result = safeModeService.evaluateSafeMode(userId);
        return ApiResponse.of(toDto(result));
    }

    @Operation(summary = "알림 목록")
    @GetMapping("/notifications")
    public ApiResponse<NotificationListResponse> listNotifications() {
        throw new NotImplementedException();
    }

    private SafeModeResponse toDto(SafeModeCheckResult result) {
        var checks = result.checks().stream()
            .map(c -> new SafeModeResponse.Check(c.key(), c.passed(), c.reason()))
            .toList();
        return new SafeModeResponse(result.active(), result.status(), checks);
    }
}
