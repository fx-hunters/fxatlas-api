package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 회차를 건너뛴 뒤 남은 회차에 금액을 재분배한다 (플래너 명세 §15).
 *
 * <pre>
 * newRemainingAmount = targetAmount - currentHeldAmount
 * newRoundAmount     = newRemainingAmount / remainingRoundCount
 * </pre>
 *
 * <p>분배는 {@link EqualSplitAllocator} 를 그대로 쓴다 — 최초 계획과 같은 반올림 규칙이어야
 * 재분배 후에도 §21-2·3(합계 일치, 잔여분은 마지막 회차만)이 유지된다.
 *
 * <p>새 회차 금액이 예산을 넘는지 판정하는 것은 여기가 아니라
 * {@link BudgetFeasibilityEvaluator} 다 — 명세 §15 는 초과 시 자동 적용하지 말고 조정 선택지를
 * 제시하라고 규정하며, 그 선택은 호출부의 몫이다.
 */
@EngineComponent
public class SkipRedistributor {

    private final EqualSplitAllocator equalSplitAllocator;

    public SkipRedistributor(EqualSplitAllocator equalSplitAllocator) {
        this.equalSplitAllocator = Objects.requireNonNull(equalSplitAllocator, "equalSplitAllocator");
    }

    /**
     * 건너뛰기 후의 재분배 결과를 계산한다.
     *
     * @param targetAmount        목표 외화 금액
     * @param currentHeldAmount   현재까지 확보한 외화 금액
     * @param remainingRoundCount 남은 회차 수 (0 이상)
     * @param minorUnits          통화 소수 자릿수
     * @return 재분배 결과. 남은 회차가 없으면 회차 목록이 비고 회차당 금액은 0이다
     * @throws IllegalArgumentException 남은 회차 수가 음수인 경우
     */
    public SkipRedistribution redistribute(
            BigDecimal targetAmount, BigDecimal currentHeldAmount, int remainingRoundCount, int minorUnits) {
        Objects.requireNonNull(targetAmount, "targetAmount");
        Objects.requireNonNull(currentHeldAmount, "currentHeldAmount");
        if (remainingRoundCount < 0) {
            throw new IllegalArgumentException("남은 회차 수는 0 이상이어야 합니다: " + remainingRoundCount);
        }

        BigDecimal newRemaining = targetAmount.subtract(currentHeldAmount).max(BigDecimal.ZERO);
        BigDecimal normalizedRemaining = equalSplitAllocator.normalize(newRemaining, minorUnits);

        if (remainingRoundCount == 0) {
            // 남은 회차가 없으면 재분배할 곳이 없다. 남은 금액은 그대로 드러내
            // 조정이 필요하다는 사실을 숨기지 않는다 (§21-8).
            return new SkipRedistribution(
                    normalizedRemaining,
                    BigDecimal.ZERO.setScale(minorUnits),
                    List.of());
        }

        List<BigDecimal> amounts =
                equalSplitAllocator.allocate(normalizedRemaining, remainingRoundCount, minorUnits);
        return new SkipRedistribution(normalizedRemaining, amounts.get(0), amounts);
    }
}
