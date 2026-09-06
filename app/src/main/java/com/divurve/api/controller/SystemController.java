package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.system.SafeModeResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.system.SafeModeService;
import com.divurve.domain.system.SafeModeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 안전모드 엔드포인트 (명세 2·3.9장).
 * 요청 주체는 {@link CurrentUser} 파라미터로 주입받는다.
 *
 * <p>홈 요약(/home/summary)과 알림 목록(/notifications)의 스텁이 여기 있었으나,
 * 이슈 #21 에서 {@code HomeController}·{@code NotificationController} 로 실구현되면서
 * 경로가 중복되어 Spring 이 ambiguous mapping 으로 기동에 실패했다(이슈 #38). 스텁을 제거했다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "안전모드")
public class SystemController {

    private final SafeModeService safeModeService;

    public SystemController(SafeModeService safeModeService) {
        this.safeModeService = safeModeService;
    }

    @Operation(summary = "안전모드 상태 (명세 3.9, FR-SF-01~05)")
    @GetMapping("/system/safe-mode")
    public ApiResponse<SafeModeResponse> getSafeMode(@CurrentUser UUID userId) {
        return ApiResponse.of(toSafeModeResponse(safeModeService.evaluateSafeMode(userId)));
    }

    private SafeModeResponse toSafeModeResponse(SafeModeView view) {
        List<SafeModeResponse.Check> checks = view.checks().stream()
                .map(c -> new SafeModeResponse.Check(c.key(), c.passed(), c.reason()))
                .toList();
        return new SafeModeResponse(view.active(), view.status(), checks);
    }
}
