package com.divurve.engine.split;

import com.divurve.engine.EngineComponent;

/**
 * 분할 횟수에 따른 분산 감소 계수 g(N) 계산 (FR-RT-08).
 *
 * g(N) = sqrt((N+1)(2N+1)/(6N²))
 *
 * 검증 표값:
 * - g(1) = 1.000
 * - g(2) = 0.791
 * - g(4) = 0.685
 * - g(6) = 0.649
 * - g(8) = 0.632
 * - g(10) = 0.620
 *
 * 권장 분할: 4~8회
 */
@EngineComponent
public class SplitVarianceReducer {

    private static final int MIN_SPLIT_COUNT = 1;
    private static final int MAX_SPLIT_COUNT = 52; // 1년 주간 개수

    /**
     * 분할 횟수 N에 대한 분산 감소 계수 g(N)을 계산한다.
     *
     * @param splitCount 분할 횟수 N (1 이상)
     * @return g(N) 계수 (0.0 < g(N) <= 1.0)
     * @throws IllegalArgumentException 분할 횟수가 1 미만이거나 52를 초과하는 경우
     */
    public double gFactor(int splitCount) {
        if (splitCount < MIN_SPLIT_COUNT || splitCount > MAX_SPLIT_COUNT) {
            throw new IllegalArgumentException(
                    "분할 횟수는 1 이상 52 이하여야 합니다 (입력: " + splitCount + ")");
        }

        double numerator = (splitCount + 1.0) * (2.0 * splitCount + 1.0);
        double denominator = 6.0 * splitCount * splitCount;
        return Math.sqrt(numerator / denominator);
    }

    /**
     * g(N-1) 대비 g(N)의 분산 감소량(시그마 개선)을 계산한다.
     *
     * @param splitCount 분할 횟수 N (2 이상)
     * @return g(N-1) - g(N) (양수 = 개선)
     * @throws IllegalArgumentException 분할 횟수가 2 미만인 경우
     */
    public double sigmaGain(int splitCount) {
        if (splitCount < 2) {
            throw new IllegalArgumentException("sigmaGain 계산은 분할 횟수 2 이상이어야 합니다 (입력: " + splitCount + ")");
        }

        double previousG = gFactor(splitCount - 1);
        double currentG = gFactor(splitCount);
        return previousG - currentG;
    }
}
