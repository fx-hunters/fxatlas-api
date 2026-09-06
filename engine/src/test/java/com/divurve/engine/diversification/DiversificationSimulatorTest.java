package com.divurve.engine.diversification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DiversificationSimulator")
class DiversificationSimulatorTest {

    private DiversificationSimulator simulator;

    @BeforeEach
    void setup() {
        simulator = new DiversificationSimulator();
    }

    @Test
    @DisplayName("시뮬레이션: 단일 통화 비중 증가 (범위 초과 예외)")
    void testSimulate_IncreaseSingleCurrency() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 1.0);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.1);

        Map<String, Double> correlation = new HashMap<>();

        // USD가 1.0에서 1.1로 증가 시도 -> 범위 초과 예외
        assertThrows(IllegalArgumentException.class, () ->
                simulator.simulate(
                        shares,
                        volatility,
                        correlation,
                        "USD",
                        0.1
                ));
    }

    @Test
    @DisplayName("시뮬레이션: 복수 통화, 한 통화 증가")
    void testSimulate_IncreaseOneCurrency() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.5);
        shares.put("EUR", 0.5);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        Map<String, Double> correlation = new HashMap<>();
        correlation.put("USD_EUR", 0.5);

        DiversificationSimulator.SimulationResult result = simulator.simulate(
                shares,
                volatility,
                correlation,
                "USD",
                0.1  // USD 비중 50% -> 60%
        );

        assertNotNull(result);
        assertTrue(result.adjustedShare().containsKey("USD"));
        assertEquals(0.6, result.adjustedShare().get("USD"), 0.0001);
        // EUR은 50% * (1 - 0.1) / (1 - 0.5) 비율로 조정
        assertTrue(result.adjustedShare().get("EUR") < 0.5, "EUR 비중이 감소해야 함");
    }

    @Test
    @DisplayName("시뮬레이션: 변동성 변화")
    void testSimulate_VolatilityChange() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.6);
        shares.put("EUR", 0.4);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        Map<String, Double> correlation = new HashMap<>();
        correlation.put("USD_EUR", 0.7);

        DiversificationSimulator.SimulationResult result = simulator.simulate(
                shares,
                volatility,
                correlation,
                "USD",
                -0.1  // USD 비중 60% -> 50%로 감소
        );

        assertNotNull(result);
        assertEquals(0.5, result.adjustedShare().get("USD"), 0.0001);
        // EUR은 40% * (1 + 0.1) / (1 - 0.6) 비율로 조정
        assertEquals(0.5, result.adjustedShare().get("EUR"), 0.0001);

        // 변동성은 대체로 감소해야 함 (더 균형 잡혀 있음)
        assertTrue(result.portfolioVolAfter() > 0, "포트폴리오 변동성은 양수");
    }

    @Test
    @DisplayName("시뮬레이션: 비중 범위 초과 예외")
    void testSimulate_OutOfBounds() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.8);
        shares.put("EUR", 0.2);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        Map<String, Double> correlation = new HashMap<>();

        // USD를 80% + 50% = 130%로 증가 시도
        assertThrows(IllegalArgumentException.class, () ->
                simulator.simulate(shares, volatility, correlation, "USD", 0.5));
    }

    @Test
    @DisplayName("시뮬레이션: 음수 비중 범위 초과 예외")
    void testSimulate_NegativeShare() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.3);
        shares.put("EUR", 0.7);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        Map<String, Double> correlation = new HashMap<>();

        // USD를 30% - 50% = -20%로 감소 시도
        assertThrows(IllegalArgumentException.class, () ->
                simulator.simulate(shares, volatility, correlation, "USD", -0.5));
    }

    @Test
    @DisplayName("시뮬레이션: 존재하지 않는 통화 예외")
    void testSimulate_NonexistentCurrency() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 1.0);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);

        Map<String, Double> correlation = new HashMap<>();

        assertThrows(IllegalArgumentException.class, () ->
                simulator.simulate(shares, volatility, correlation, "GBP", 0.1));
    }

    @Test
    @DisplayName("시뮬레이션: null 비중 맵 예외")
    void testSimulate_NullShareMap() {
        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);

        Map<String, Double> correlation = new HashMap<>();

        assertThrows(NullPointerException.class, () ->
                simulator.simulate(null, volatility, correlation, "USD", 0.1));
    }

    @Test
    @DisplayName("시뮬레이션: null 변동성 맵 예외")
    void testSimulate_NullVolatilityMap() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.5);
        shares.put("EUR", 0.5);

        Map<String, Double> correlation = new HashMap<>();

        assertThrows(NullPointerException.class, () ->
                simulator.simulate(shares, null, correlation, "USD", 0.1));
    }

    @Test
    @DisplayName("시뮬레이션: null 상관계수 맵 예외")
    void testSimulate_NullCorrelationMap() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.5);
        shares.put("EUR", 0.5);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        assertThrows(NullPointerException.class, () ->
                simulator.simulate(shares, volatility, null, "USD", 0.1));
    }

    @Test
    @DisplayName("시뮬레이션: null 통화 예외")
    void testSimulate_NullCurrency() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.5);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);

        Map<String, Double> correlation = new HashMap<>();

        assertThrows(NullPointerException.class, () ->
                simulator.simulate(shares, volatility, correlation, null, 0.1));
    }

    @Test
    @DisplayName("시뮬레이션: 대칭 상관계수 조회")
    void testSimulate_SymmetricCorrelation() {
        Map<String, Double> shares = new HashMap<>();
        shares.put("USD", 0.5);
        shares.put("EUR", 0.5);

        Map<String, Double> volatility = new HashMap<>();
        volatility.put("USD", 0.12);
        volatility.put("EUR", 0.14);

        // EUR_USD 순서로 저장 (USD_EUR이 아님)
        Map<String, Double> correlation = new HashMap<>();
        correlation.put("EUR_USD", 0.5);

        DiversificationSimulator.SimulationResult result = simulator.simulate(
                shares,
                volatility,
                correlation,
                "USD",
                0.05
        );

        assertNotNull(result);
        assertTrue(result.portfolioVolBefore() > 0);
        assertTrue(result.portfolioVolAfter() > 0);
    }
}
