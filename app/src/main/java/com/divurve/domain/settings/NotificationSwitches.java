package com.divurve.domain.settings;

/**
 * 알림 스위치 5종 변경 요청 (API 명세 v2 §3 {@code PUT /me/settings}, ERD v3.0 {@code user_settings}).
 *
 * <p>모든 필드가 {@code Boolean} 이고 {@code null} 은 <b>미변경</b>을 뜻한다 — 부분 수정을 허용하기 위해서다.
 * 값 자체는 기능 플래그일 뿐 계산에 들어가지 않는다(FR-MY-05·FR-MY-06).
 *
 * @param notifyStepDue       회차 집행 예정 알림
 * @param notifyRegimeShift   시장 국면 전환 알림
 * @param notifyDeadlineNear  마감 임박 알림
 * @param notifyTargetZone    목표 구간 진입 알림 (기본값 false)
 * @param notifyConcentration 집중도 경고 알림
 */
public record NotificationSwitches(
        Boolean notifyStepDue,
        Boolean notifyRegimeShift,
        Boolean notifyDeadlineNear,
        Boolean notifyTargetZone,
        Boolean notifyConcentration) {

    /** 아무것도 바꾸지 않는 요청. 설명 프로필만 수정할 때 쓴다. */
    public static NotificationSwitches unchanged() {
        return new NotificationSwitches(null, null, null, null, null);
    }
}
