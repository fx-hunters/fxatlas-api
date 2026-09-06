package com.divurve.engine.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StressCalculator")
class StressCalculatorTest {

    private StressCalculator calculator;

    @BeforeEach
    void setup() {
        calculator = new StressCalculator();
    }

    @Test
    @DisplayName("스트레스 계산: 단일 통화 양수 충격")
    void testApply_SingleCurrency_PositiveShock() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01); // +1%

        StressCalculator.StressResult result = calculator.apply(assets, rates, shocks);

        assertNotNull(result);
        assertEquals(1000000L, result.totalAssetBeforeKrw());
        assertEquals(1010000L, result.totalAssetAfterKrw());
        assertEquals(10000L, result.portfolioImpactKrw());
        assertEquals(0.01, result.portfolioImpactRatio(), 0.0001);
    }

    @Test
    @DisplayName("스트레스 계산: 단일 통화 음수 충격")
    void testApply_SingleCurrency_NegativeShock() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", -0.05); // -5%

        StressCalculator.StressResult result = calculator.apply(assets, rates, shocks);

        assertNotNull(result);
        assertEquals(1000000L, result.totalAssetBeforeKrw());
        assertEquals(950000L, result.totalAssetAfterKrw());
        assertEquals(-50000L, result.portfolioImpactKrw());
        assertEquals(-0.05, result.portfolioImpactRatio(), 0.0001);
    }

    @Test
    @DisplayName("스트레스 계산: 복수 통화")
    void testApply_MultipleCurrencies() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);
        assets.put("EUR", 800.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));
        rates.put("EUR", new BigDecimal("1100"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);  // +1%
        shocks.put("EUR", -0.02); // -2%

        StressCalculator.StressResult result = calculator.apply(assets, rates, shocks);

        assertNotNull(result);
        assertEquals(1880000L, result.totalAssetBeforeKrw());
        // USD: 1000000 * 1.01 = 1010000
        // EUR: 880000 * 0.98 = 862400
        assertEquals(1872400L, result.totalAssetAfterKrw());
        assertEquals(-7600L, result.portfolioImpactKrw());
    }

    @Test
    @DisplayName("스트레스 계산: 충격 지정 안 된 통화는 0% 변화")
    void testApply_UnspecifiedCurrency() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);
        assets.put("EUR", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));
        rates.put("EUR", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01); // EUR 충격은 지정 안 함

        StressCalculator.StressResult result = calculator.apply(assets, rates, shocks);

        assertNotNull(result);
        // USD: 1000000 -> 1010000 (+10000)
        // EUR: 1000000 -> 1000000 (0)
        assertEquals(2000000L, result.totalAssetBeforeKrw());
        assertEquals(2010000L, result.totalAssetAfterKrw());
    }

    @Test
    @DisplayName("스트레스 계산: 음수 자산 예외")
    void testApply_NegativeAsset() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", -1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.apply(assets, rates, shocks));
    }

    @Test
    @DisplayName("스트레스 계산: null 환율 예외")
    void testApply_NullRate() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", null);

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.apply(assets, rates, shocks));
    }

    @Test
    @DisplayName("스트레스 계산: 환율 0 예외")
    void testApply_ZeroRate() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("0"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.apply(assets, rates, shocks));
    }

    @Test
    @DisplayName("스트레스 계산: null 자산맵 예외")
    void testApply_NullAssetMap() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        assertThrows(NullPointerException.class, () ->
                calculator.apply(null, rates, shocks));
    }

    @Test
    @DisplayName("스트레스 계산: null 환율맵 예외")
    void testApply_NullRateMap() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        assertThrows(NullPointerException.class, () ->
                calculator.apply(assets, null, shocks));
    }

    @Test
    @DisplayName("스트레스 계산: 자산 0일 때 영향비율 0")
    void testApply_ZeroAsset() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 0.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        Map<String, Double> shocks = new HashMap<>();
        shocks.put("USD", 0.01);

        StressCalculator.StressResult result = calculator.apply(assets, rates, shocks);

        assertNotNull(result);
        assertEquals(0L, result.totalAssetBeforeKrw());
        assertEquals(0.0, result.portfolioImpactRatio(), 0.0001);
    }

    @Test
    @DisplayName("스트레스 계산: null 충격맵 예외")
    void testApply_NullShockMap() {
        Map<String, Double> assets = new HashMap<>();
        assets.put("USD", 1000.0);

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1000"));

        assertThrows(NullPointerException.class, () ->
                calculator.apply(assets, rates, null));
    }
}
