package com.divurve.engine.planner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 회차를 건너뛴 뒤의 재분배 결과 (플래너 명세 §15).
 *
 * <p>이것은 <b>미리보기</b>다 — 명세 §15 는 건너뛰기가 현재 계획을 즉시 덮어쓰지 않고 변경
 * 계획을 계산해 사용자 승인을 받도록 규정한다. 승인 전에는 활성 계획이 그대로여야 한다
 * (§21-9).
 *
 * @param newRemainingAmount 재분배 후 남은 외화
 * @param perRoundAmount     남은 회차당 금액 (남은 회차가 없으면 0)
 * @param roundAmounts       남은 회차별 금액. 합은 {@code newRemainingAmount} 와 같다
 */
public record SkipRedistribution(
        BigDecimal newRemainingAmount,
        BigDecimal perRoundAmount,
        List<BigDecimal> roundAmounts) {

    public SkipRedistribution {
        Objects.requireNonNull(newRemainingAmount, "newRemainingAmount");
        Objects.requireNonNull(perRoundAmount, "perRoundAmount");
        roundAmounts = List.copyOf(Objects.requireNonNull(roundAmounts, "roundAmounts"));
    }
}
