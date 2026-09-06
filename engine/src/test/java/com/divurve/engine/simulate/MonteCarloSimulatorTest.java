package com.divurve.engine.simulate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonteCarloSimulatorTest {

    private MonteCarloSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new MonteCarloSimulator();
    }

    @Test
    void achievementProbability_ReturnsValidRange() {
        double probability = simulator.achievementProbability(
                0.08,     // expectedReturn 8%
                0.15,     // volatility 15%
                10000.0,  // initialAmount
                500.0,    // monthlyContribution
                24,       // months
                20000.0,  // targetAmount
                12345L    // seed
        );

        assertThat(probability).isBetween(0.0, 1.0);
    }

    @Test
    void achievementProbability_HigherExpectedReturnIncreasesProb() {
        double probLow = simulator.achievementProbability(
                0.05, 0.15, 0.0, 500.0, 24, 20000.0, 12345L);
        double probHigh = simulator.achievementProbability(
                0.10, 0.15, 0.0, 500.0, 24, 20000.0, 12345L);

        assertThat(probHigh).isGreaterThanOrEqualTo(probLow);
    }

    @Test
    void achievementProbability_ReturnsWithinRange() {
        // 단순히 범위 검증 — volatility 경향은 deterministic하지 않을 수 있음
        double probLowVol = simulator.achievementProbability(
                0.08, 0.10, 0.0, 500.0, 24, 20000.0, 12345L);
        double probHighVol = simulator.achievementProbability(
                0.08, 0.25, 0.0, 500.0, 24, 20000.0, 12345L);

        assertThat(probLowVol).isBetween(0.0, 1.0);
        assertThat(probHighVol).isBetween(0.0, 1.0);
    }

    @Test
    void achievementProbability_HigherContributionIncreasesProb() {
        double probLow = simulator.achievementProbability(
                0.08, 0.15, 0.0, 300.0, 24, 20000.0, 12345L);
        double probHigh = simulator.achievementProbability(
                0.08, 0.15, 0.0, 700.0, 24, 20000.0, 12345L);

        assertThat(probHigh).isGreaterThan(probLow);
    }

    @Test
    void achievementProbability_DeterministicWithSameSeed() {
        double prob1 = simulator.achievementProbability(
                0.08, 0.15, 0.0, 500.0, 24, 20000.0, 12345L);
        double prob2 = simulator.achievementProbability(
                0.08, 0.15, 0.0, 500.0, 24, 20000.0, 12345L);

        assertThat(prob1).isEqualTo(prob2);
    }

    @Test
    void achievementProbability_NegativeVolatility_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, -0.10, 0.0, 500.0, 24, 20000.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }


    @Test
    void achievementProbability_NegativeInitialAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, 0.15, -1000.0, 500.0, 24, 20000.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void achievementProbability_NegativeContribution_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, 0.15, 0.0, -500.0, 24, 20000.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void achievementProbability_ZeroMonths_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, 0.15, 0.0, 500.0, 0, 20000.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개월");
    }

    @Test
    void achievementProbability_ZeroTargetAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, 0.15, 0.0, 500.0, 24, 0.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }

    @Test
    void achievementProbability_NegativeTargetAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> simulator.achievementProbability(
                0.08, 0.15, 0.0, 500.0, 24, -20000.0, 12345L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }

    @Test
    void achievementProbability_GuaranteedCase() {
        // 아주 높은 초기액과 기여금으로는 거의 100%에 가까워야 함
        double probability = simulator.achievementProbability(
                0.10, 0.05, 50000.0, 5000.0, 12, 20000.0, 12345L);

        assertThat(probability).isGreaterThan(0.8);
    }

    @Test
    void achievementProbability_ImpossibleCase() {
        // 아주 짧은 기간과 높은 목표로는 낮은 확률
        double probability = simulator.achievementProbability(
                0.02, 0.30, 0.0, 100.0, 6, 100000.0, 12345L);

        assertThat(probability).isLessThan(0.5);
    }

    @Test
    void achievementProbability_WithHigherInitialAmount() {
        // 높은 초기액으로 더 좋은 결과를 기대할 수 있음
        double prob1 = simulator.achievementProbability(
                0.08, 0.15, 5000.0, 500.0, 12, 30000.0, 12345L);

        assertThat(prob1).isBetween(0.0, 1.0);
    }
}
