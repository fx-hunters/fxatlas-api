package com.divurve.api.dto.me;

/** 알림 설정 수정 요청 (PUT /me/notifications). */
public record NotificationSettingsRequest(
        Boolean exchangeScheduleReminder,
        Boolean reviewRequiredAlert,
        Boolean deadlineApproachAlert,
        Boolean bucketEntryAlert) {
}
