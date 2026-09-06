package com.divurve.engine.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    // ── v2 시나리오 계산 (요구사항 §4.8, 명세 §5.9) ──────────────────────────

    @Nested
    @DisplayName("applyScenario — 주가·환율·총액 효과 분리")
    class ApplyScenario {

        /** 명세 §4 Mock fixture: 해외주식 20,000,000 · 외화자산 24,720,000. */
        private static final long EQUITY_KRW = 20_000_000L;
        private static final long FX_ASSET_KRW = 24_720_000L;

        @Test
        @DisplayName("명세 §5.9 검산 — 주가 -4,000,000 / 환율 +2,072,000 / 합계 -1,928,000 / 적용 후 22,792,000")
        void specFixture() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -0.20, 0.10);

            assertEquals(-4_000_000L, result.equityEffectKrw());
            assertEquals(2_072_000L, result.fxEffectKrw());
            assertEquals(-1_928_000L, result.totalEffectKrw());
            assertEquals(22_792_000L, result.fxAssetAfterKrw());
            assertEquals(StressCalculator.FX_CUSHIONS_EQUITY_LOSS, result.interpretationCode());
            assertEquals(EQUITY_KRW, result.equityAssetKrw());
            assertEquals(FX_ASSET_KRW, result.fxAssetBeforeKrw());
            assertEquals(-0.20, result.equityShockPct());
            assertEquals(0.10, result.fxShockPct());
        }

        @Test
        @DisplayName("두 효과의 합은 항상 총효과와 정확히 같다 (적용 순서 고정의 결과)")
        void effectsSumExactly() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -0.20, 0.10);

            assertEquals(result.totalEffectKrw(), result.equityEffectKrw() + result.fxEffectKrw());
        }

        @Test
        @DisplayName("주가 하락 + 원화 강세 — 두 효과가 모두 손실 방향")
        void equityDownKrwStrong() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -0.20, -0.10);

            assertEquals(-4_000_000L, result.equityEffectKrw());
            assertEquals(-2_072_000L, result.fxEffectKrw());
            assertEquals(-6_072_000L, result.totalEffectKrw());
            assertEquals(18_648_000L, result.fxAssetAfterKrw());
            assertEquals(StressCalculator.EQUITY_AND_FX_BOTH_NEGATIVE, result.interpretationCode());
        }

        @Test
        @DisplayName("환율 충격 0 도 손실 방향으로 본다 (주가 손실을 덜어주지 않으므로)")
        void zeroFxShockCountsAsNegativeSide() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -0.20, 0.0);

            assertEquals(0L, result.fxEffectKrw());
            assertEquals(StressCalculator.EQUITY_AND_FX_BOTH_NEGATIVE, result.interpretationCode());
        }

        @Test
        @DisplayName("환율 효과가 주가 손실을 전부 상쇄하면 offsets")
        void fxOffsetsEquityLoss() {
            // 주가 -1,000,000 → 환율 기준 9,000,000 × +0.20 = +1,800,000 → 총 +800,000
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(10_000_000L, 10_000_000L, -0.10, 0.20);

            assertEquals(-1_000_000L, result.equityEffectKrw());
            assertEquals(1_800_000L, result.fxEffectKrw());
            assertEquals(800_000L, result.totalEffectKrw());
            assertEquals(StressCalculator.FX_OFFSETS_EQUITY_LOSS, result.interpretationCode());
        }

        @Test
        @DisplayName("주가 효과가 손실이 아닌데 환율이 깎으면 fx_reduces_equity_gain")
        void fxReducesEquityGain() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(10_000_000L, 10_000_000L, 0.10, -0.10);

            assertEquals(1_000_000L, result.equityEffectKrw());
            assertEquals(-1_100_000L, result.fxEffectKrw());
            assertEquals(StressCalculator.FX_REDUCES_EQUITY_GAIN, result.interpretationCode());
        }

        @Test
        @DisplayName("두 효과 모두 손실이 아니면 both_positive")
        void bothPositive() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(10_000_000L, 10_000_000L, 0.10, 0.10);

            assertEquals(StressCalculator.EQUITY_AND_FX_BOTH_POSITIVE, result.interpretationCode());
        }

        @Test
        @DisplayName("충격 0/0 은 아무것도 바꾸지 않는다")
        void noShock() {
            StressCalculator.ScenarioResult result =
                    calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, 0.0, 0.0);

            assertEquals(0L, result.totalEffectKrw());
            assertEquals(FX_ASSET_KRW, result.fxAssetAfterKrw());
            assertEquals(StressCalculator.EQUITY_AND_FX_BOTH_POSITIVE, result.interpretationCode());
        }

        @Test
        @DisplayName("자산이 음수면 예외")
        void negativeAssets() {
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(-1L, FX_ASSET_KRW, -0.20, 0.10));
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(0L, -1L, -0.20, 0.10));
        }

        @Test
        @DisplayName("해외주식이 외화자산보다 크면 예외")
        void equityExceedsFxAsset() {
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(FX_ASSET_KRW + 1L, FX_ASSET_KRW, -0.20, 0.10));
        }

        @Test
        @DisplayName("충격률이 범위를 벗어나거나 NaN 이면 예외")
        void invalidShock() {
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -1.5, 0.10));
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, 10.5, 0.10));
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, Double.NaN, 0.10));
            assertThrows(IllegalArgumentException.class,
                    () -> calculator.applyScenario(EQUITY_KRW, FX_ASSET_KRW, -0.20, -1.5));
        }
    }
}
