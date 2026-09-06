package com.divurve.api.dto.me;

/** 알림 설정 응답 (PUT /me/notifications). */
public record NotificationSettingsResponse(
        boolean exchangeScheduleReminder,
        boolean reviewRequiredAlert,
        boolean deadlineApproachAlert,
        boolean bucketEntryAlert) {
}
