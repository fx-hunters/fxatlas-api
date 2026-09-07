package com.divurve.engine.planner;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 정해진 원화 예산으로 확보할 수 있는 외화 범위 (플래너 명세 §10.2).
 *
 * <p>비용 범위({@link CostRange})와 <b>방향이 반대</b>다 — 환율이 높을수록 같은 예산으로
 * 확보할 수 있는 외화는 <b>줄어든다</b>. 그래서 {@code low} 는 환율 상단에서, {@code high} 는
 * 환율 하단에서 나온다.
 *
 * @param low  환율 상단 기준 확보 외화 (가장 적음)
 * @param base 기준 환율 확보 외화
 * @param high 환율 하단 기준 확보 외화 (가장 많음)
 */
public record AcquisitionRange(BigDecimal low, BigDecimal base, BigDecimal high) {

    public AcquisitionRange {
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(high, "high");
    }

    /**
     * 같은 회차가 반복될 때의 누적 확보 외화 범위 (명세 §10.3 "점검 시점의 누적 외화 범위").
     *
     * <p>모든 회차가 같은 환율 범위 안에 있다고 가정한 <b>조건부 범위</b>다. 실제 결과를
     * 보장하지 않으며, 명세 §10.3 은 이 안내를 응답에 함께 담으라고 규정한다.
     *
     * @param roundCount 회차 수 (0 이상)
     * @return 회차 수를 곱한 누적 범위
     * @throws IllegalArgumentException 회차 수가 음수인 경우
     */
    public AcquisitionRange accumulate(int roundCount) {
        if (roundCount < 0) {
            throw new IllegalArgumentException("회차 수는 0 이상이어야 합니다: " + roundCount);
        }
        BigDecimal multiplier = BigDecimal.valueOf(roundCount);
        return new AcquisitionRange(
                low.multiply(multiplier),
                base.multiply(multiplier),
                high.multiply(multiplier));
    }
}
