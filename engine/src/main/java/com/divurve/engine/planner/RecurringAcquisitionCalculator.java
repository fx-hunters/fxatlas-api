package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 정기형 — 회차 예산으로 확보할 수 있는 외화 범위 (플래너 명세 §10.2).
 *
 * <pre>
 * netBudget          = 회차 예산 - 수수료
 * foreignAmountLow   = netBudget / rHigh
 * foreignAmountBase  = netBudget / rBase
 * foreignAmountHigh  = netBudget / rLow
 * </pre>
 *
 * <p>환율에는 스프레드를 반영한다 — {@link ExchangeCostCalculator#effectiveRate} 를 그대로 써서
 * 마감형 비용 계산의 정확한 역함수가 되게 한다. 두 계산이 다른 환율을 쓰면 "이 예산으로 이만큼
 * 준비"와 "이만큼 준비에 이 비용"이 서로 어긋난다.
 *
 * <p>확보 외화는 통화 최소 단위로 <b>내림</b>한다 — 실제로 확보되지 않을 금액을 표시하지 않기
 * 위해서다.
 */
@EngineComponent
public class RecurringAcquisitionCalculator {

    /** 나눗셈 중간 정밀도. 최종 결과는 통화 최소 단위로 다시 내림한다. */
    private static final MathContext DIVISION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private final ExchangeCostCalculator exchangeCostCalculator;

    public RecurringAcquisitionCalculator(ExchangeCostCalculator exchangeCostCalculator) {
        this.exchangeCostCalculator = Objects.requireNonNull(
                exchangeCostCalculator, "exchangeCostCalculator");
    }

    /**
     * 회차 예산에서 수수료를 뺀 실사용 예산 (명세 §10.2).
     *
     * @param budgetKrw 회차 원화 예산 (0 이상)
     * @param feeKrw    정액 수수료 (원, 0 이상)
     * @return 수수료를 뺀 예산. 수수료가 예산보다 크면 0
     * @throws IllegalArgumentException 예산이나 수수료가 음수인 경우
     */
    public long netBudget(long budgetKrw, long feeKrw) {
        if (budgetKrw < 0) {
            throw new IllegalArgumentException("회차 예산은 0 이상이어야 합니다: " + budgetKrw);
        }
        if (feeKrw < 0) {
            throw new IllegalArgumentException("수수료는 0 이상이어야 합니다: " + feeKrw);
        }
        return Math.max(budgetKrw - feeKrw, 0L);
    }

    /**
     * 회차 예산으로 확보할 수 있는 외화 범위 (명세 §10.2).
     *
     * @param netBudgetKrw 수수료를 제외한 회차 예산 (0 이상)
     * @param rates        환율 범위 (per-unit 정규화 완료)
     * @param spreadRatio  스프레드 비율
     * @param minorUnits   통화 소수 자릿수
     * @return 확보 가능한 외화 범위
     * @throws IllegalArgumentException 예산이 음수이거나 소수 자릿수가 음수인 경우
     */
    public AcquisitionRange acquirableRange(
            long netBudgetKrw, RateRange rates, double spreadRatio, int minorUnits) {
        Objects.requireNonNull(rates, "rates");
        if (netBudgetKrw < 0) {
            throw new IllegalArgumentException("예산은 0 이상이어야 합니다: " + netBudgetKrw);
        }
        if (minorUnits < 0) {
            throw new IllegalArgumentException("통화 소수 자릿수는 0 이상이어야 합니다: " + minorUnits);
        }

        BigDecimal budget = BigDecimal.valueOf(netBudgetKrw);
        return new AcquisitionRange(
                divide(budget, rates.high(), spreadRatio, minorUnits),
                divide(budget, rates.base(), spreadRatio, minorUnits),
                divide(budget, rates.low(), spreadRatio, minorUnits));
    }

    private BigDecimal divide(BigDecimal budget, BigDecimal rate, double spreadRatio, int minorUnits) {
        return budget
                .divide(exchangeCostCalculator.effectiveRate(rate, spreadRatio), DIVISION_CONTEXT)
                .setScale(minorUnits, RoundingMode.DOWN);
    }
}
