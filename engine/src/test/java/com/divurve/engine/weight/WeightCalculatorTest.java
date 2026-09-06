package com.divurve.engine.weight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WeightCalculator")
class WeightCalculatorTest {

    private WeightCalculator calculator;

    @BeforeEach
    void setup() {
        calculator = new WeightCalculator();
    }

    @Test
    @DisplayName("외화 비중 계산: 정상값")
    void testCalculateFxRatio_Normal() {
        double ratio = calculator.calculateFxRatio(1000000L, 300000L);
        assertEquals(0.3, ratio, 0.0001);
    }

    @Test
    @DisplayName("외화 비중 계산: 외화 0")
    void testCalculateFxRatio_ZeroFx() {
        double ratio = calculator.calculateFxRatio(1000000L, 0L);
        assertEquals(0.0, ratio);
    }

    @Test
    @DisplayName("외화 비중 계산: 총자산 0")
    void testCalculateFxRatio_ZeroTotal() {
        double ratio = calculator.calculateFxRatio(0L, 0L);
        assertEquals(0.0, ratio);
    }

    @Test
    @DisplayName("외화 비중 계산: 100% 외화")
    void testCalculateFxRatio_AllFx() {
        double ratio = calculator.calculateFxRatio(500000L, 500000L);
        assertEquals(1.0, ratio, 0.0001);
    }

    @Test
    @DisplayName("외화 비중 계산: 음수 총자산 예외")
    void testCalculateFxRatio_NegativeTotal() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateFxRatio(-1000000L, 300000L));
    }

    @Test
    @DisplayName("외화 비중 계산: 음수 외화자산 예외")
    void testCalculateFxRatio_NegativeFx() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateFxRatio(1000000L, -300000L));
    }

    @Test
    @DisplayName("통화별 비중 계산: 정상값")
    void testCalculateCurrencyShare_Normal() {
        double share = calculator.calculateCurrencyShare(200000L, 500000L);
        assertEquals(0.4, share, 0.0001);
    }

    @Test
    @DisplayName("통화별 비중 계산: 외화자산 0")
    void testCalculateCurrencyShare_ZeroFx() {
        double share = calculator.calculateCurrencyShare(100000L, 0L);
        assertEquals(0.0, share);
    }

    @Test
    @DisplayName("통화별 비중 계산: 음수 통화자산 예외")
    void testCalculateCurrencyShare_NegativeCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateCurrencyShare(-100000L, 500000L));
    }

    @Test
    @DisplayName("통화별 비중 계산: 음수 외화자산 예외")
    void testCalculateCurrencyShare_NegativeFx() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateCurrencyShare(100000L, -500000L));
    }

    @Test
    @DisplayName("Exposure 맵 계산: 단일 통화")
    void testCalculateExposureMap_SingleCurrency() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);

        Map<String, Double> exposure = calculator.calculateExposureMap(assets, 300000L);

        assertEquals(1, exposure.size());
        assertEquals(1.0, exposure.get("USD"), 0.0001);
    }

    @Test
    @DisplayName("Exposure 맵 계산: 복수 통화")
    void testCalculateExposureMap_MultipleCurrencies() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);
        assets.put("EUR", 200000L);
        assets.put("JPY", 500000L);

        Map<String, Double> exposure = calculator.calculateExposureMap(assets, 1000000L);

        assertEquals(3, exposure.size());
        assertEquals(0.3, exposure.get("USD"), 0.0001);
        assertEquals(0.2, exposure.get("EUR"), 0.0001);
        assertEquals(0.5, exposure.get("JPY"), 0.0001);
    }

    @Test
    @DisplayName("Exposure 맵 계산: 외화자산 0")
    void testCalculateExposureMap_ZeroFxAsset() {
        Map<String, Long> assets = new HashMap<>();

        Map<String, Double> exposure = calculator.calculateExposureMap(assets, 0L);

        assertEquals(0, exposure.size());
    }
}
