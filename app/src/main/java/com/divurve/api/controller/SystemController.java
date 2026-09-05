package com.divurve.api.controller;

import com.divurve.api.dto.system.HomeSummaryResponse;
import com.divurve.api.dto.system.NotificationListResponse;
import com.divurve.api.dto.system.SafeModeResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기타(홈 요약·안전모드·알림 목록) 엔드포인트 스텁 (명세 2·3.9장).
 * 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "홈 요약·안전모드·알림 목록")
public class SystemController {

    @Operation(summary = "홈 3블록 통합 조회")
    @GetMapping("/home/summary")
    public ApiResponse<HomeSummaryResponse> getHomeSummary() {
        throw new NotImplementedException();
    }

    @Operation(summary = "안전모드 상태")
    @GetMapping("/system/safe-mode")
    public ApiResponse<SafeModeResponse> getSafeMode() {
        throw new NotImplementedException();
    }

    @Operation(summary = "알림 목록")
    @GetMapping("/notifications")
    public ApiResponse<NotificationListResponse> listNotifications() {
        throw new NotImplementedException();
    }
}
