package com.divurve.domain.settings;

/**
 * 사용자 설정 조회 결과 (이슈 #10, FR-MY-04). 저장된 설정값에 더해 실효 스프레드 계산 결과를 함께 담는다.
 *
 * @param defaultBankCode      주거래 은행 코드 (미지정 가능)
 * @param fxDiscountRatio      환전 우대율 (0.0~1.0)
 * @param displayMode          표시(설명) 모드 — 금액·위험 판정에 영향 없음(FR-MY-03)
 * @param baseSpreadRatio      은행 기본 스프레드 비율 (마스터 조회값)
 * @param effectiveSpreadRatio 실효 스프레드 비율 = base × (1 − discount) (engine 계산값)
 */
public record SettingsView(
        String defaultBankCode,
        double fxDiscountRatio,
        String displayMode,
        double baseSpreadRatio,
        double effectiveSpreadRatio) {
}
