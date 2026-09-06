package com.divurve.domain.settings;

/**
 * 사용자 설정 조회 결과 (API 명세 v2 §3 마이페이지 표, FR-MY-03~FR-MY-06, ERD v3.0 {@code user_settings}).
 * 저장된 설정값·알림 스위치 5종에 더해 실효 스프레드 계산 결과를 함께 담는다.
 *
 * <p>알림 스위치는 v1 의 {@code PUT /me/notifications} 를 흡수한 것이다 — 명세 §3 은 {@code GET/PUT /me/settings}
 * 하나가 설명 프로필과 알림 스위치를 함께 다루도록 정했다.
 *
 * @param defaultBankCode      주거래 은행 코드 (미지정 가능)
 * @param fxDiscountRatio      환전 우대율 (0.0~1.0)
 * @param explainLevel         설명 선호 3단계 (simple/standard/detailed) — 금액·위험 판정에 영향 없음(FR-MY-03)
 * @param explainDomain        익숙한 설명 분야 (finance/dev/marketing/plain) — 표현에만 사용
 * @param baseSpreadRatio      은행 기본 스프레드 비율 (마스터 조회값)
 * @param effectiveSpreadRatio 실효 스프레드 비율 = base × (1 − discount) (engine 계산값)
 * @param notifyStepDue        회차 집행 예정 알림
 * @param notifyRegimeShift    시장 국면 전환 알림
 * @param notifyDeadlineNear   마감 임박 알림
 * @param notifyTargetZone     목표 구간 진입 알림 — ERD 기본값만 false
 * @param notifyConcentration  집중도 경고 알림
 */
public record SettingsView(
        String defaultBankCode,
        double fxDiscountRatio,
        String explainLevel,
        String explainDomain,
        double baseSpreadRatio,
        double effectiveSpreadRatio,
        boolean notifyStepDue,
        boolean notifyRegimeShift,
        boolean notifyDeadlineNear,
        boolean notifyTargetZone,
        boolean notifyConcentration) {
}
