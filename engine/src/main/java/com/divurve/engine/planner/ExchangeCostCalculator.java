package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 외화 준비에 드는 예상 원화 비용 (플래너 명세 §9.3).
 *
 * <pre>
 * cost(amount, rate) = amount × rate × (1 + spreadRate) + fee
 * </pre>
 *
 * <p>기존 {@code CostCalculator} 와 다른 계산이다 — 그쪽은 이미 정해진 원화 총액에 스프레드를
 * 곱하고 회차당 정액 수수료를 더한다. 여기서는 <b>외화 금액과 환율</b>에서 원화 비용을 만든다.
 */
@EngineComponent
public class ExchangeCostCalculator {

    /**
     * 스프레드를 반영한 실효 환율 {@code rate × (1 + spreadRatio)}.
     *
     * <p>정기형의 확보 외화 계산({@link RecurringAcquisitionCalculator})은 이 실효 환율의
     * 역수를 쓴다 — 두 방향이 같은 환율을 써야 "이 예산으로 이만큼 준비"와 "이만큼 준비에 이 비용"이
     * 서로 어긋나지 않는다.
     *
     * @param perUnitRate 외화 1단위당 원화
     * @param spreadRatio 스프레드 비율 (0 이상)
     * @return 실효 환율
     * @throws IllegalArgumentException 환율이 0 이하이거나 스프레드가 음수인 경우
     */
    public BigDecimal effectiveRate(BigDecimal perUnitRate, double spreadRatio) {
        Objects.requireNonNull(perUnitRate, "perUnitRate");
        if (perUnitRate.signum() <= 0) {
            throw new IllegalArgumentException("환율은 0보다 커야 합니다: " + perUnitRate);
        }
        if (spreadRatio < 0.0) {
            throw new IllegalArgumentException("스프레드 비율은 0 이상이어야 합니다: " + spreadRatio);
        }
        return perUnitRate.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(spreadRatio)));
    }

    /**
     * 외화 금액을 준비하는 데 드는 예상 원화 비용 (명세 §9.3).
     *
     * @param amount      준비할 외화 금액 (0 이상)
     * @param perUnitRate 외화 1단위당 원화
     * @param spreadRatio 스프레드 비율
     * @param feeKrw      정액 수수료 (원, 0 이상)
     * @return 원 단위로 반올림한 예상 비용
     * @throws IllegalArgumentException 금액이 음수이거나 수수료가 음수인 경우
     */
    public long cost(BigDecimal amount, BigDecimal perUnitRate, double spreadRatio, long feeKrw) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("외화 금액은 0 이상이어야 합니다: " + amount);
        }
        if (feeKrw < 0) {
            throw new IllegalArgumentException("수수료는 0 이상이어야 합니다: " + feeKrw);
        }
        return amount.multiply(effectiveRate(perUnitRate, spreadRatio))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact() + feeKrw;
    }

    /**
     * 환율 범위별 예상 비용 3종 (명세 §9.3).
     *
     * @param amount      준비할 외화 금액
     * @param rates       환율 범위 (per-unit 정규화 완료)
     * @param spreadRatio 스프레드 비율
     * @param feeKrw      정액 수수료 (원)
     * @return 하단·기준·상단 비용
     */
    public CostRange costRange(BigDecimal amount, RateRange rates, double spreadRatio, long feeKrw) {
        Objects.requireNonNull(rates, "rates");
        return new CostRange(
                cost(amount, rates.low(), spreadRatio, feeKrw),
                cost(amount, rates.base(), spreadRatio, feeKrw),
                cost(amount, rates.high(), spreadRatio, feeKrw));
    }
}
