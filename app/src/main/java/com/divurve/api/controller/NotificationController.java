package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.notifications.NotificationsResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
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
    public ApiResponse<NotificationsResponse> getNotifications() {
        // 현재 사용자 확인
        currentUserId();
        // TODO: 실제 알림 목록 조회 구현
        return ApiResponse.of(new NotificationsResponse(List.of()));
    }

    /** 현재 요청 주체의 사용자 id. 인증 컨텍스트가 없으면 401. */
    private UUID currentUserId() {
        return CurrentUserContext.get()
                .map(AuthPrincipal::userId)
                .orElseThrow(UnauthorizedException::new);
    }
}
