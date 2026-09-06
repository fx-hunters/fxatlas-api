package com.divurve.engine.weight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    // --- API 명세 v2 §4 Mock fixture 재현 ---

    @Test
    @DisplayName("명세 §4 fixture — 외화 24,720,000 / 총자산 68,400,000 → 외화 비중 0.3614")
    void fixture_외화_비중() {
        // 명세 §5.3 예시는 3자리 표기(0.361), 엔진은 §1.4 비율 규약대로 4자리로 낸다.
        assertEquals(0.3614, calculator.calculateFxRatio(68_400_000L, 24_720_000L), 1e-9);
    }

    @Test
    @DisplayName("명세 §5.3 fixture — 환율 1% 민감도 157,900 / 54,700 / 34,600, 합 247,200")
    void fixture_민감도_1퍼센트() {
        WeightCalculator.Sensitivity sensitivity =
                calculator.calculateSensitivity1pct(fixtureExposure());

        assertEquals(157_900L, sensitivity.byCurrency().get("USD"));
        assertEquals(54_700L, sensitivity.byCurrency().get("JPY"));
        assertEquals(34_600L, sensitivity.byCurrency().get("EUR"));
        assertEquals(247_200L, sensitivity.totalKrw());
    }

    @Test
    @DisplayName("명세 §5.3 fixture — 통화별 비중 0.6388 / 0.2213 / 0.1400")
    void fixture_통화별_비중() {
        Map<String, Double> exposure =
                calculator.calculateExposureMap(fixtureExposure(), 24_720_000L);

        assertEquals(0.6388, exposure.get("USD"), 1e-9);
        assertEquals(0.2213, exposure.get("JPY"), 1e-9);
        assertEquals(0.1400, exposure.get("EUR"), 1e-9);
    }

    @Test
    @DisplayName("민감도: 입력 순서를 유지하고 0원은 0을 낸다")
    void 민감도는_입력_순서를_유지한다() {
        Map<String, Long> assets = new LinkedHashMap<>();
        assets.put("USD", 0L);
        assets.put("EUR", 1_000_000L);

        WeightCalculator.Sensitivity sensitivity = calculator.calculateSensitivity1pct(assets);

        assertEquals(List.of("USD", "EUR"), List.copyOf(sensitivity.byCurrency().keySet()));
        assertEquals(0L, sensitivity.byCurrency().get("USD"));
        assertEquals(10_000L, sensitivity.byCurrency().get("EUR"));
        assertEquals(10_000L, sensitivity.totalKrw());
    }

    @Test
    @DisplayName("민감도: 빈 포트폴리오는 합계 0")
    void 민감도_빈_포트폴리오() {
        WeightCalculator.Sensitivity sensitivity =
                calculator.calculateSensitivity1pct(new LinkedHashMap<>());

        assertEquals(0L, sensitivity.totalKrw());
        assertEquals(0, sensitivity.byCurrency().size());
    }

    @Test
    @DisplayName("민감도: 통화 금액이 음수면 예외")
    void 민감도_음수_예외() {
        Map<String, Long> assets = new LinkedHashMap<>();
        assets.put("USD", -1L);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateSensitivity1pct(assets));
    }

    /** 명세 §4 Mock fixture 의 통화별 노출 (합 24,720,000원). */
    private static Map<String, Long> fixtureExposure() {
        Map<String, Long> exposure = new LinkedHashMap<>();
        exposure.put("USD", 15_790_000L);
        exposure.put("JPY", 5_470_000L);
        exposure.put("EUR", 3_460_000L);
        return exposure;
    }
}
