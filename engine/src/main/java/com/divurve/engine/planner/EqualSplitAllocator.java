package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 남은 외화를 회차에 균등 분배한다 (플래너 명세 §9.5).
 *
 * <p>불변조건 §21-2·3 — <b>회차 금액의 합은 남은 외화와 정확히 같고, 반올림 잔여분은 마지막
 * 회차에만 반영한다.</b> 그래서 앞 회차는 통화 최소 단위로 <b>내림</b>하고 남은 차액을 마지막
 * 회차가 흡수한다. 올림이나 반올림을 쓰면 앞 회차 합이 총액을 넘어 마지막 회차가 음수가 될 수
 * 있다.
 *
 * <p>{@code double} 이 아니라 {@link BigDecimal} 로 다룬다 — 합계 일치가 불변조건이므로
 * 이진 부동소수점 오차를 허용할 수 없다.
 */
@EngineComponent
public class EqualSplitAllocator {

    /**
     * 총액을 회차 수로 균등 분배한다.
     *
     * <p>총액은 먼저 통화 최소 단위로 반올림된다 — 그 반올림된 값이 합계 기준이며,
     * {@link #normalize} 로 같은 값을 얻을 수 있다.
     *
     * @param totalAmount 분배할 총 외화 금액 (0 이상)
     * @param roundCount  회차 수 (1 이상)
     * @param minorUnits  통화 소수 자릿수 (JPY 0, 대부분 2)
     * @return seq 순서의 회차 금액. 합은 정규화된 총액과 정확히 같다
     * @throws IllegalArgumentException 총액이 음수이거나, 회차 수가 1 미만이거나, 소수 자릿수가 음수인 경우
     */
    public List<BigDecimal> allocate(BigDecimal totalAmount, int roundCount, int minorUnits) {
        BigDecimal total = normalize(totalAmount, minorUnits);
        if (roundCount < 1) {
            throw new IllegalArgumentException("회차 수는 1 이상이어야 합니다: " + roundCount);
        }

        BigDecimal perRound = total.divide(BigDecimal.valueOf(roundCount), minorUnits, RoundingMode.DOWN);

        List<BigDecimal> amounts = new ArrayList<>(roundCount);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < roundCount - 1; i++) {
            amounts.add(perRound);
            allocated = allocated.add(perRound);
        }
        // 잔여분은 마지막 회차에만 (§21-3). 앞 회차를 내림했으므로 항상 perRound 이상이다.
        amounts.add(total.subtract(allocated));
        return amounts;
    }

    /**
     * 금액을 통화 최소 단위로 반올림한다 (명세 §8 "통화별 소수 자릿수와 최소 단위를 검증한다").
     *
     * @param amount     금액 (0 이상)
     * @param minorUnits 통화 소수 자릿수 (0 이상)
     * @return minorUnits 자리로 반올림한 금액
     * @throws IllegalArgumentException 금액이 음수이거나 소수 자릿수가 음수인 경우
     */
    public BigDecimal normalize(BigDecimal amount, int minorUnits) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다: " + amount);
        }
        if (minorUnits < 0) {
            throw new IllegalArgumentException("통화 소수 자릿수는 0 이상이어야 합니다: " + minorUnits);
        }
        return amount.setScale(minorUnits, RoundingMode.HALF_UP);
    }
}
