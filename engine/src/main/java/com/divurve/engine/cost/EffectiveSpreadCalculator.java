package com.divurve.engine.cost;

import com.divurve.engine.EngineComponent;

/**
 * 실효 환전 스프레드 계산기 (이슈 #10, FR-MY-04). 은행 고시 기본 스프레드에 환전 우대율을 적용해
 * 사용자가 실제로 부담하는 실효 스프레드를 결정론적으로 계산한다.
 *
 * <p>{@code effective_spread_ratio = base_spread_ratio × (1 − fx_discount_ratio)}.
 * 예) 기본 1.75%에 우대율 80% → 실효 0.35%. 은행별 기본 스프레드 마스터 조회는 도메인의 책임이고,
 * 여기서는 곱셈만 순수하게 수행한다(부작용·프레임워크 의존 없음).
 */
@EngineComponent
public class EffectiveSpreadCalculator {

    /**
     * 실효 스프레드 비율을 계산한다.
     *
     * @param baseSpreadRatio 은행 기본 스프레드 비율 (0 이상)
     * @param fxDiscountRatio 환전 우대율 (0.0~1.0, 예: 0.8 = 80% 우대)
     * @return 실효 스프레드 비율
     * @throws IllegalArgumentException 기본 스프레드가 음수거나 우대율이 [0,1] 범위를 벗어난 경우
     */
    public double effectiveSpreadRatio(double baseSpreadRatio, double fxDiscountRatio) {
        if (baseSpreadRatio < 0.0) {
            throw new IllegalArgumentException("기본 스프레드는 음수일 수 없습니다 (입력 " + baseSpreadRatio + ").");
        }
        if (fxDiscountRatio < 0.0 || fxDiscountRatio > 1.0) {
            throw new IllegalArgumentException("환전 우대율은 0.0~1.0 이어야 합니다 (입력 " + fxDiscountRatio + ").");
        }
        return baseSpreadRatio * (1.0 - fxDiscountRatio);
    }
}
