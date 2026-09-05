package com.divurve.api.dto.system;

import java.util.List;

/** 알림 목록 응답 (GET /notifications). */
public record NotificationListResponse(List<Notification> notifications) {

    /** 개별 알림. */
    public record Notification(
            String id,
            String type,
            String message,
            boolean isRead,
            String createdAt) {
    }
}
