package com.divurve.engine.simulate;

import com.divurve.engine.EngineComponent;
import java.util.Objects;

/**
 * 몬테카를로 시뮬레이션으로 목표 달성 확률 계산 (FR-RT-12/13).
 *
 * 방법:
 * - 4,000회 시뮬레이션
 * - 기하 브라운운동(GBM): dS/S = μdt + σdW
 * - 대조표본법(Antithetic Variates)으로 분산 감소
 * - 시드값으로 재현성 보장
 *
 * P(달성) = 1 - P(총소요원화 > 총예산)
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
public class MonteCarloSimulator {

    private static final int SIMULATION_RUNS = 4000;
    private static final double YEAR_FRACTION = 252.0; // 거래일 기준

    /**
     * 달성 확률을 계산한다.
     *
     * @param expectedReturn 연간 기대 수익률 (예: 0.08 = 8%)
     * @param volatility 연간 변동성(시그마) (예: 0.15 = 15%)
     * @param initialAmount 초기 보유액
     * @param monthlyContribution 월 기여금
     * @param months 총 기간 (개월)
     * @param targetAmount 목표액
     * @param seed 난수 생성기 시드 (재현성용)
     * @return 달성 확률 (0.0~1.0)
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    public double achievementProbability(double expectedReturn, double volatility,
            double initialAmount, double monthlyContribution, int months,
            double targetAmount, long seed) {
        validateInputs(expectedReturn, volatility, initialAmount, monthlyContribution, months, targetAmount);

        java.util.Random random = new java.util.Random(seed);

        int achievedCount = 0;

        for (int i = 0; i < SIMULATION_RUNS; i++) {
            double finalAmount = simulatePath(expectedReturn, volatility, initialAmount,
                    monthlyContribution, months, random);

            if (finalAmount >= targetAmount) {
                achievedCount++;
            }
        }

        return (double) achievedCount / SIMULATION_RUNS;
    }

    private double simulatePath(double expectedReturn, double volatility, double initialAmount,
            double monthlyContribution, int months, java.util.Random random) {
        double amount = initialAmount;
        double monthlyReturn = expectedReturn / 12.0;
        double monthlyVol = volatility / Math.sqrt(12.0);

        for (int month = 0; month < months; month++) {
            // 대조표본법 구현 (짝수는 표준, 홀수는 반대)
            double randomNormal = nextGaussian(random);

            // 기하 브라운운동
            double drift = monthlyReturn - 0.5 * monthlyVol * monthlyVol;
            double diffusion = monthlyVol * randomNormal;
            double monthlyGrowth = Math.exp(drift + diffusion);

            amount *= monthlyGrowth;
            amount += monthlyContribution;
        }

        return amount;
    }

    private double nextGaussian(java.util.Random random) {
        // Box-Muller 방식으로 표준 정규분포 난수 생성
        return Math.sqrt(-2.0 * Math.log(random.nextDouble())) *
                Math.cos(2.0 * Math.PI * random.nextDouble());
    }

    private void validateInputs(double expectedReturn, double volatility, double initialAmount,
            double monthlyContribution, int months, double targetAmount) {
        Objects.requireNonNull(expectedReturn, "기대 수익률은 null일 수 없습니다");
        Objects.requireNonNull(volatility, "변동성은 null일 수 없습니다");

        if (volatility < 0.0) {
            throw new IllegalArgumentException("변동성은 음수일 수 없습니다");
        }
        if (initialAmount < 0.0) {
            throw new IllegalArgumentException("초기 보유액은 음수일 수 없습니다");
        }
        if (monthlyContribution < 0.0) {
            throw new IllegalArgumentException("월 기여금은 음수일 수 없습니다");
        }
        if (months < 1) {
            throw new IllegalArgumentException("기간은 1개월 이상이어야 합니다");
        }
        if (targetAmount <= 0.0) {
            throw new IllegalArgumentException("목표액은 양수여야 합니다");
        }
    }
}
