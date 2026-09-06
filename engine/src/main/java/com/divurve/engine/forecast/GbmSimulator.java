package com.divurve.engine.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 기하 브라우니안 모션(Geometric Brownian Motion, GBM) 시뮬레이터.
 *
 * <p>dS = μ·S·dt + σ·S·dW
 * 여기서 μ는 드리프트(기대 수익률), σ는 변동성(표준편차), dW는 위너 프로세스.
 *
 * <p>팬차트의 경로 데이터를 생성하는 데 사용되며, 모든 시뮬레이션은
 * 재현 가능하도록 시드를 받을 수 있다.
 */
public class GbmSimulator {

    private final Random random;

    /**
     * 시뮬레이터 생성.
     *
     * @param seed 난수 생성기 시드 (재현성 확보)
     */
    public GbmSimulator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * GBM 경로 집합을 시뮬레이션한다.
     *
     * @param initialRate 초기 환율 (t=0)
     * @param drift 기대 수익률 (μ, 연환산)
     * @param volatility 변동성 (σ, 연환산)
     * @param horizonDays 미래 지평 (일수)
     * @param numPaths 시뮬레이션 경로 수
     * @return 경로 집합 (행=경로 수, 열=시점, 각 원소는 해당 시점의 환율)
     */
    public List<List<Double>> simulate(
        double initialRate,
        double drift,
        double volatility,
        int horizonDays,
        int numPaths
    ) {
        Objects.requireNonNull(initialRate, "initialRate must not be null");
        if (initialRate <= 0) {
            throw new IllegalArgumentException("initialRate must be positive");
        }
        if (volatility <= 0) {
            throw new IllegalArgumentException("volatility must be positive");
        }
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("horizonDays must be positive");
        }
        if (numPaths <= 0) {
            throw new IllegalArgumentException("numPaths must be positive");
        }

        List<List<Double>> paths = new ArrayList<>();

        for (int p = 0; p < numPaths; p++) {
            List<Double> path = simulateSinglePath(initialRate, drift, volatility, horizonDays);
            paths.add(path);
        }

        return paths;
    }

    /**
     * 단일 GBM 경로를 시뮬레이션한다 (매일 기준).
     *
     * @param initialRate 초기값
     * @param drift 기대 수익률 (연환산)
     * @param volatility 변동성 (연환산)
     * @param horizonDays 미래 일수
     * @return 일별 환율 경로 (길이: horizonDays + 1, 첫 원소는 initialRate)
     */
    private List<Double> simulateSinglePath(double initialRate, double drift, double volatility, int horizonDays) {
        List<Double> path = new ArrayList<>();
        path.add(initialRate);

        double dt = 1.0 / 252.0; // 일별 (영업일 252일 기준)
        double driftPerDay = drift * dt;
        double volPerDay = volatility * Math.sqrt(dt);

        double currentRate = initialRate;

        for (int day = 1; day <= horizonDays; day++) {
            double randomShock = random.nextGaussian();
            double logReturn = driftPerDay + volPerDay * randomShock;
            currentRate = currentRate * Math.exp(logReturn);
            path.add(currentRate);
        }

        return path;
    }

    /**
     * 드리프트 0 경로 시뮬레이션 (기준선 생성용).
     * 이 경우 예상값은 항상 초기값이다.
     *
     * @param initialRate 초기값
     * @param volatility 변동성 (연환산)
     * @param horizonDays 미래 일수
     * @param numPaths 시뮬레이션 경로 수
     * @return 드리프트 0 경로들
     */
    public List<List<Double>> simulateZeroDrift(
        double initialRate,
        double volatility,
        int horizonDays,
        int numPaths
    ) {
        return simulate(initialRate, 0.0, volatility, horizonDays, numPaths);
    }
}
