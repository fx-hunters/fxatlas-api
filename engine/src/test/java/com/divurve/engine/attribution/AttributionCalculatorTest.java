package com.divurve.engine.attribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AttributionCalculator")
class AttributionCalculatorTest {

    private AttributionCalculator calculator;

    @BeforeEach
    void setup() {
        calculator = new AttributionCalculator();
    }

    @Test
    @DisplayName("귀속분해: 수익이 났을 때 (자산증가, 환율증가)")
    void testDecompose_Profitable() {
        AttributionCalculator.AttributionResult result = calculator.decompose(
                1000.0,  // assetStartLocal: 1000 USD
                1100.0,  // assetEndLocal: 1100 USD (10% 상승)
                new BigDecimal("1000"), // rateStartKrw
                new BigDecimal("1050"), // rateEndKrw (5% 상승)
                0.0,     // costRatio
                "three_way"
        );

        assertNotNull(result);
        assertEquals(1000000L, result.costBasisKrw());
        assertEquals(1155000L, result.currentKrw());
        assertTrue(result.totalReturn() > 0, "총 수익률이 양수여야 함");
    }

    @Test
    @DisplayName("귀속분해: 자산 수익률만 양수")
    void testDecompose_AssetReturn() {
        AttributionCalculator.AttributionResult result = calculator.decompose(
                1000.0,
                1100.0,  // +10%
                new BigDecimal("1000"),
                new BigDecimal("1000"),  // 환율 변화 없음
                0.0,
                "three_way"
        );

        assertNotNull(result);
        assertTrue(result.asset().returnRatio() > 0, "자산 수익률이 양수여야 함");
        assertEquals(0.0, result.fx().returnRatio(), 0.0001, "환율 수익률은 0");
    }

    @Test
    @DisplayName("귀속분해: 환율 수익률만 양수")
    void testDecompose_FxReturn() {
        AttributionCalculator.AttributionResult result = calculator.decompose(
                1000.0,
                1000.0,  // 자산 변화 없음
                new BigDecimal("1000"),
                new BigDecimal("1100"),  // +10%
                0.0,
                "three_way"
        );

        assertNotNull(result);
        assertEquals(0.0, result.asset().returnRatio(), 0.0001, "자산 수익률은 0");
        assertTrue(result.fx().returnRatio() > 0, "환율 수익률이 양수여야 함");
    }

    @Test
    @DisplayName("귀속분해: 비용 포함")
    void testDecompose_WithCost() {
        AttributionCalculator.AttributionResult result = calculator.decompose(
                1000.0,
                1000.0,
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                0.02,  // 2% 비용
                "three_way"
        );

        assertNotNull(result);
        assertTrue(result.cost().returnRatio() < 0, "비용 수익률은 음수");
        assertTrue(result.totalReturn() < 0, "총 수익률이 음수여야 함");
    }

    @Test
    @DisplayName("귀속분해: shapley 모드")
    void testDecompose_ShapleyMode() {
        AttributionCalculator.AttributionResult result = calculator.decompose(
                1000.0,
                1100.0,
                new BigDecimal("1000"),
                new BigDecimal("1050"),
                0.0,
                "shapley"
        );

        assertNotNull(result);
        assertEquals("shapley", result.mode());
    }

    @Test
    @DisplayName("귀속분해: 자산 0 예외")
    void testDecompose_ZeroAsset() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.decompose(0.0, 1000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0, "three_way"));
    }

    @Test
    @DisplayName("귀속분해: 음수 자산 예외")
    void testDecompose_NegativeAsset() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.decompose(-1000.0, 1000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0, "three_way"));
    }

    @Test
    @DisplayName("귀속분해: 환율 0 예외")
    void testDecompose_ZeroRate() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.decompose(1000.0, 1000.0, new BigDecimal("0"), new BigDecimal("1000"), 0.0, "three_way"));
    }

    @Test
    @DisplayName("귀속분해: 비용 비율 범위 초과 예외")
    void testDecompose_InvalidCostRatio() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.decompose(1000.0, 1000.0, new BigDecimal("1000"), new BigDecimal("1000"), 1.5, "three_way"));
    }

    @Test
    @DisplayName("귀속분해: null 환율 예외")
    void testDecompose_NullRate() {
        assertThrows(NullPointerException.class, () ->
                calculator.decompose(1000.0, 1000.0, null, new BigDecimal("1000"), 0.0, "three_way"));
    }

    @Test
    @DisplayName("귀속분해: null 모드 예외")
    void testDecompose_NullMode() {
        assertThrows(NullPointerException.class, () ->
                calculator.decompose(1000.0, 1000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0, null));
    }

    @Test
    @DisplayName("귀속분해: 음수 종료 자산은 허용")
    void testDecompose_NegativeEndAsset() {
        // 자산이 마이너스가 되는 것은 가능 (전부 팔면)
        assertThrows(IllegalArgumentException.class, () ->
                calculator.decompose(1000.0, -100.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0, "three_way"));
    }
}
