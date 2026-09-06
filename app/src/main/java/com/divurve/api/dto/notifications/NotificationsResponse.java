package com.divurve.api.dto.notifications;

import java.time.Instant;
import java.util.List;

/** 알림 목록 조회 응답 (GET /notifications). */
public record NotificationsResponse(List<NotificationDto> notifications) {

    /** 개별 알림 정보. */
    public record NotificationDto(
            String id,
            String type,
            String title,
            String message,
            Instant createdAt,
            boolean read) {
    }
}
