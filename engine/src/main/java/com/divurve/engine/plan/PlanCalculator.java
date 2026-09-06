package com.divurve.engine.plan;

import static java.util.Objects.requireNonNull;

/**
 * 계획 회차 관련 순수 계산 함수.
 * Spring/JPA 의존 없음. 모든 메서드는 상태를 변경하지 않는 순수 함수.
 */
public class PlanCalculator {

    private static final double EPSILON = 1e-10;

    private PlanCalculator() {
    }

    /**
     * 현재 확보율 계산.
     *
     * @param executedAmount 지금까지 실행한 외화 금액
     * @param targetAmount   목표 외화 금액
     * @return 확보율 (0.0 ~ 1.0, 1.0 이상은 그대로)
     */
    public static double calculateSecuredRatio(double executedAmount, double targetAmount) {
        if (targetAmount < EPSILON) {
            return 0.0;
        }
        return Math.min(executedAmount / targetAmount, 1.0);
    }

    /**
     * 남은 금액 재계산.
     *
     * @param targetAmount   목표 외화 금액
     * @param executedAmount 현재까지 실행한 금액
     * @return 남은 금액
     */
    public static double calculateRemainingAmount(double targetAmount, double executedAmount) {
        return Math.max(0.0, targetAmount - executedAmount);
    }

    /**
     * 건너뛴 회차를 제외한 남은 회차수 계산.
     *
     * @param totalSteps   전체 회차수
     * @param currentSeq   현재 회차번호 (1부터 시작)
     * @param skippedCount 건너뛴 회차 누적수
     * @return 남은 실행 가능 회차수
     */
    public static int calculateRemainingSteps(int totalSteps, int currentSeq, int skippedCount) {
        int executed = currentSeq - 1; // 0부터 시작하는 인덱스
        int remaining = totalSteps - executed - skippedCount;
        return Math.max(0, remaining);
    }

    /**
     * 건너뛰기로 인한 회차당 부담 증가분 계산.
     * 남은 금액을 남은 회차로 나누어 새로운 부담(amount)을 계산한다.
     *
     * @param remainingAmount 남은 외화 금액
     * @param remainingSteps  남은 회차수
     * @param currentAmount   현재 회차 부담 금액
     * @return 증가분 비율 (예: 0.15 = 15% 증가)
     */
    public static double calculateBurdenIncreaseRatio(
            double remainingAmount, int remainingSteps, double currentAmount) {
        if (remainingSteps <= 0 || currentAmount < EPSILON) {
            return 0.0;
        }

        double newAmount = remainingAmount / remainingSteps;
        if (currentAmount < EPSILON) {
            return 0.0;
        }

        return (newAmount - currentAmount) / currentAmount;
    }

    /**
     * 건너뛰기로 인한 달성 확률 변화 계산 (단순 휴리스틱).
     * 회차 수가 줄어들수록 달성 확률이 선형으로 감소한다고 가정.
     * achieveProb_new = achieveProb_old * (remainingSteps / totalSteps)
     *
     * @param currentAchieveProb 현재 달성 확률 (0.0 ~ 1.0)
     * @param totalSteps         전체 회차수
     * @param remainingSteps     남은 회차수
     * @return 변경 후 달성 확률
     */
    public static double calculateAchieveProbAfterSkip(
            double currentAchieveProb, int totalSteps, int remainingSteps) {
        if (totalSteps <= 0) {
            return 0.0;
        }
        return currentAchieveProb * ((double) remainingSteps / totalSteps);
    }

    /**
     * 연속 건너뛰기 여부 판정.
     * 임계치(3회)에 도달하면 true.
     *
     * @param consecutiveSkips 연속 건너뛰기 누적 카운트
     * @return 안전 모드 시작 여부
     */
    public static boolean shouldTriggerSafeMode(int consecutiveSkips) {
        return consecutiveSkips >= 3;
    }
}
