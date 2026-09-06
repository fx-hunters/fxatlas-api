package com.divurve.engine.cost;

import com.divurve.engine.EngineComponent;
import java.util.Objects;

/**
 * 회차별 환전 비용 계산 (스프레드 + 고정수수료) (FR-RT-11).
 *
 * 총비용 = (안전 버킷 KRW 합계) × 실효스프레드율 + (회차 수) × 고정수수료
 */
@EngineComponent
public class CostCalculator {

    /**
     * 총 환전 비용을 계산한다.
     *
     * @param totalSafeAmountKrw 안전 버킷 총액 (KRW)
     * @param effectiveSpreadRatio 실효 스프레드 비율 (0.0~1.0)
     * @param splitCount 회차 수
     * @param fixedFeePerStepKrw 회차당 고정 수수료 (KRW)
     * @return 총 비용 (KRW)
     * @throws IllegalArgumentException 입력값이 음수이거나 스프레드 비율이 범위를 벗어난 경우
     */
    public long totalCost(double totalSafeAmountKrw, double effectiveSpreadRatio, int splitCount,
            long fixedFeePerStepKrw) {
        validateInputs(totalSafeAmountKrw, effectiveSpreadRatio, splitCount, fixedFeePerStepKrw);

        long spreadCost = Math.round(totalSafeAmountKrw * effectiveSpreadRatio);
        long fixedCost = (long) splitCount * fixedFeePerStepKrw;
        return spreadCost + fixedCost;
    }

    /**
     * 스프레드 비용만 계산한다.
     *
     * @param totalSafeAmountKrw 안전 버킷 총액 (KRW)
     * @param effectiveSpreadRatio 실효 스프레드 비율
     * @return 스프레드 비용 (KRW)
     */
    public long spreadCost(double totalSafeAmountKrw, double effectiveSpreadRatio) {
        if (totalSafeAmountKrw < 0.0) {
            throw new IllegalArgumentException("안전 버킷 총액은 음수일 수 없습니다");
        }
        if (effectiveSpreadRatio < 0.0 || effectiveSpreadRatio > 1.0) {
            throw new IllegalArgumentException("실효 스프레드 비율은 0.0~1.0 이어야 합니다");
        }

        return Math.round(totalSafeAmountKrw * effectiveSpreadRatio);
    }

    /**
     * 고정 수수료만 계산한다.
     *
     * @param splitCount 회차 수
     * @param fixedFeePerStepKrw 회차당 고정 수수료 (KRW)
     * @return 고정 수수료 합계 (KRW)
     */
    public long fixedCost(int splitCount, long fixedFeePerStepKrw) {
        if (splitCount < 1) {
            throw new IllegalArgumentException("회차 수는 1 이상이어야 합니다");
        }
        if (fixedFeePerStepKrw < 0L) {
            throw new IllegalArgumentException("고정 수수료는 음수일 수 없습니다");
        }

        return (long) splitCount * fixedFeePerStepKrw;
    }

    private void validateInputs(double totalSafeAmountKrw, double effectiveSpreadRatio,
            int splitCount, long fixedFeePerStepKrw) {
        if (totalSafeAmountKrw < 0.0) {
            throw new IllegalArgumentException("안전 버킷 총액은 음수일 수 없습니다");
        }
        if (effectiveSpreadRatio < 0.0 || effectiveSpreadRatio > 1.0) {
            throw new IllegalArgumentException("실효 스프레드 비율은 0.0~1.0 이어야 합니다");
        }
        if (splitCount < 1) {
            throw new IllegalArgumentException("회차 수는 1 이상이어야 합니다");
        }
        if (fixedFeePerStepKrw < 0L) {
            throw new IllegalArgumentException("고정 수수료는 음수일 수 없습니다");
        }
    }
}
