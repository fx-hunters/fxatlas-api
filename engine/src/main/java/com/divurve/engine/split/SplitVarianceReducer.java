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
 * 권장 분할: 4~8회 (후보값)
 *
 * <p><b>⚠ 요구사항 v2 §4.12 미확정</b> — 이 클래스의 상수와 판정 기준은 <b>후보일 뿐 확정
 * 요구사항이 아니다</b>. 요구사항 v2 §4.12 는 Route 의 목적함수 · 최소 입력값 · 안전/기회 버킷
 * 존재와 비율 · 목적별 하한선 · 권장 분할 회차 · 몬테카를로 적용 여부 · 달성 확률 정의를 전부
 * 미확정으로 선언했고, 기존 문서의 50/70/85/95% 비율과 4~8회 권장값도 후보값이다.
 * API 명세 v2 §6.3 은 확정 전까지 이 값들을 <b>명세하지 않는다</b>고 못박는다.
 *
 * <p>그래서 계산기는 남겨 두되 {@code route.enabled} 기능 플래그가 꺼진 동안에는 호출되지 않는다
 * — 호출 경로인 {@code /api/v1/plans/*} 가 501 을 반환한다. 값이 확정되면 이 주석과 상수를 함께
 * 갱신하고, 커밋 타입은 {@code calc} 로 변경 전/후 수치를 남긴다.
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
