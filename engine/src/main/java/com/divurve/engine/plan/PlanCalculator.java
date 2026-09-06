package com.divurve.engine.plan;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 계획 회차 관련 순수 계산 함수.
 * Spring/JPA 의존 없음. 모든 메서드는 상태를 변경하지 않는 순수 함수.
 *
 * <p><b>⚠ 요구사항 v2 §4.12 미확정</b> — 달성 확률의 정의 자체가 미확정이므로
 * {@link #calculateAchieveProbAfterSkip} 의 선형 휴리스틱은 <b>후보일 뿐 확정 요구사항이 아니다</b>.
 * 계산기는 남겨 두되 {@code route.enabled} 가 꺼진 동안에는 호출되지 않는다 — {@code /api/v1/plans/*}
 * 가 501 을 반환한다(명세 v2 §6).
 *
 * <p>v1 의 {@code shouldTriggerSafeMode(연속 건너뛰기 ≥ 3 → 안전모드)} 는 <b>삭제했다</b>.
 * v1 안전모드 기능 자체가 v2 에서 제거됐고, 임계치 3 역시 §4.12 의 미확정 값이었다.
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
     * @param currentSeq   처리한 회차수 (누적)
     * @param skippedCount 건너뛴 회차 누적수
     * @return 남은 실행 가능 회차수
     */
    public static int calculateRemainingSteps(int totalSteps, int currentSeq, int skippedCount) {
        int remaining = totalSteps - currentSeq - skippedCount;
        return Math.max(0, remaining);
    }

    /**
     * 건너뛰기로 인한 회차당 부담 증가분 계산.
     * 남은 금액을 남은 회차로 나누어 새로운 부담(amount)을 계산한다.
     * 남은 금액이 음수이면 새로운 부담을 0으로 처리 (이미 충분히 매입).
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

        double newAmount = Math.max(0.0, remainingAmount) / remainingSteps;
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
     * 총액을 splitCount 회차로 균등분할한 스케줄을 생성한다.
     * (계획 미리보기의 균등분할 공식 {@code amount = totalAmount / splitCount} 과 동일 —
     * 계획 확정 시에도 같은 공식으로 회차를 만들어 미리보기와 확정 결과가 어긋나지 않게 한다.)
     *
     * @param totalAmount  분배할 총 금액 (예: 안전 버킷 금액)
     * @param splitCount   분할 회차수 (1 이상)
     * @param intervalDays 회차 간 간격(일)
     * @param startDate    첫 회차 예정일
     * @return seq 1..splitCount 순서의 회차 스케줄
     * @throws IllegalArgumentException splitCount 가 1 미만인 경우
     */
    public static List<ScheduledStep> generateEqualSplitSchedule(
            double totalAmount, int splitCount, int intervalDays, LocalDate startDate) {
        requireNonNull(startDate, "startDate");
        if (splitCount < 1) {
            throw new IllegalArgumentException("splitCount는 1 이상이어야 합니다: " + splitCount);
        }

        double amountPerStep = totalAmount / splitCount;
        List<ScheduledStep> schedule = new ArrayList<>();
        for (int seq = 1; seq <= splitCount; seq++) {
            LocalDate scheduledDate = startDate.plusDays((long) intervalDays * (seq - 1));
            schedule.add(new ScheduledStep(seq, scheduledDate, amountPerStep));
        }
        return schedule;
    }

    /**
     * 균등분할 스케줄의 단일 회차.
     *
     * @param seq           회차 번호 (1부터 시작)
     * @param scheduledDate 예정일
     * @param amount        회차 부담 금액
     */
    public record ScheduledStep(int seq, LocalDate scheduledDate, double amount) {
    }
}
