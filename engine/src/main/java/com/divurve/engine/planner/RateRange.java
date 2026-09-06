package com.divurve.engine.planner;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 계산에 쓰는 환율 범위 (플래너 명세 §7·§9.1).
 *
 * <p>세 값 모두 <b>외화 1단위당 원화</b>로 정규화된 뒤 들어와야 한다 — 명세 §7 은 100엔 기준
 * 고시를 1엔 기준으로 바꾼 뒤 계산하라고 규정하고, §21-6 이 이를 불변조건으로 못박는다.
 * 정규화는 {@code QuoteUnitNormalizer} 가 담당한다.
 *
 * <p>이 범위는 <b>방향 전망이 아니다</b> (명세 §9.3). 같은 외화 금액을 준비할 때 환율에 따라
 * 원화 비용이 얼마나 달라질 수 있는지를 나타낼 뿐이며, 모델의 방향 경로({@code model_path})는
 * 계획 계산의 입력이 되지 않는다.
 *
 * @param low  범위 하단 {@code rLow}
 * @param base 기준 환율 {@code rBase}
 * @param high 범위 상단 {@code rHigh}
 */
public record RateRange(BigDecimal low, BigDecimal base, BigDecimal high) {

    public RateRange {
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(high, "high");
        if (low.signum() <= 0) {
            throw new IllegalArgumentException("환율은 0보다 커야 합니다: low=" + low);
        }
        if (low.compareTo(base) > 0 || base.compareTo(high) > 0) {
            throw new IllegalArgumentException(
                    "환율 범위는 low <= base <= high 여야 합니다: " + low + ", " + base + ", " + high);
        }
    }
}
