package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.notifications.NotificationsResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 엔드포인트 (이슈 #21).
 * 사용자의 알림 목록을 조회한다. 현재는 기본 구현으로 빈 목록을 반환한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "사용자 알림")
public class NotificationController {

    @Operation(summary = "알림 목록 조회")
    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(@CurrentUser UUID userId) {
        // TODO: userId 기준으로 실제 알림 목록을 조회한다.
        // @CurrentUser 파라미터 자체가 인증을 강제하므로 별도 확인 호출이 필요 없다.
        return ApiResponse.of(new NotificationsResponse(List.of()));
    }
}
