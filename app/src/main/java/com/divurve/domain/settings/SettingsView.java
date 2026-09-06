package com.divurve.domain.settings;

/**
 * 사용자 설정 조회 결과 (이슈 #10, FR-MY-04, ERD v3.0). 저장된 설정값에 더해 실효 스프레드 계산 결과를 함께 담는다.
 *
 * @param defaultBankCode      주거래 은행 코드 (미지정 가능)
 * @param fxDiscountRatio      환전 우대율 (0.0~1.0)
 * @param explainLevel         설명 선호 3단계 (simple/standard/detailed) — 금액·위험 판정에 영향 없음(FR-MY-03)
 * @param explainDomain        익숙한 설명 분야 (finance/dev/marketing/plain) — 표현에만 사용
 * @param baseSpreadRatio      은행 기본 스프레드 비율 (마스터 조회값)
 * @param effectiveSpreadRatio 실효 스프레드 비율 = base × (1 − discount) (engine 계산값)
 */
public record SettingsView(
        String defaultBankCode,
        double fxDiscountRatio,
        String explainLevel,
        String explainDomain,
        double baseSpreadRatio,
        double effectiveSpreadRatio) {
}
