package com.divurve.engine.concentration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcentrationCalculator")
class ConcentrationCalculatorTest {

    private ConcentrationCalculator calculator;

    @BeforeEach
    void setup() {
        calculator = new ConcentrationCalculator();
    }

    @Test
    void calculateConcentration_SingleCurrency_Returns1() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 1000.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_EqualDistribution_Returns025() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 250.0);
        holdings.put("EUR", 250.0);
        holdings.put("GBP", 250.0);
        holdings.put("JPY", 250.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isCloseTo(0.25, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_Unequal_Between() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 600.0);
        holdings.put("EUR", 400.0);

        double concentration = calculator.calculateConcentration(holdings);

        // (0.6^2 + 0.4^2) = 0.36 + 0.16 = 0.52
        assertThat(concentration).isCloseTo(0.52, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void calculateConcentration_Empty_Returns0() {
        Map<String, Double> holdings = new HashMap<>();

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isEqualTo(0.0);
    }

    @Test
    void calculateConcentration_Null_ThrowsNullPointerException() {
        assertThatThrownBy(() -> calculator.calculateConcentration(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void calculateConcentration_ZeroTotal_Returns0() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 0.0);
        holdings.put("EUR", 0.0);

        double concentration = calculator.calculateConcentration(holdings);

        assertThat(concentration).isEqualTo(0.0);
    }

    @Test
    void verdictConcentrationChange_Improves() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.40);

        assertThat(verdict).isEqualTo("improves");
    }

    @Test
    void verdictConcentrationChange_Worsens() {
        String verdict = calculator.verdictConcentrationChange(0.40, 0.50);

        assertThat(verdict).isEqualTo("worsens");
    }

    @Test
    void verdictConcentrationChange_Neutral_SmallDelta() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.51);

        assertThat(verdict).isEqualTo("neutral");
    }

    @Test
    void verdictConcentrationChange_Neutral_JustAtThreshold() {
        String verdict = calculator.verdictConcentrationChange(0.50, 0.515);

        assertThat(verdict).isEqualTo("neutral");
    }

    @Test
    void report_ValidInputs_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 600.0);
        before.put("EUR", 400.0);

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 700.0);
        after.put("EUR", 300.0);

        var report = calculator.report(before, after);

        assertThat(report.verdict()).isEqualTo("worsens");
        assertThat(report.threshold()).isEqualTo(0.02);
    }

    @Test
    void report_Null_ThrowsNullPointerException() {
        Map<String, Double> holdings = new HashMap<>();
        holdings.put("USD", 1000.0);

        assertThatThrownBy(() -> calculator.report(null, holdings))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> calculator.report(holdings, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void report_NormalizedProportions() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 100.0);
        before.put("EUR", 100.0);

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 200.0);
        after.put("EUR", 300.0);

        var report = calculator.report(before, after);

        // 정규화 확인
        assertThat(report.before().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
        assertThat(report.after().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void report_EmptyBefore_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();

        Map<String, Double> after = new HashMap<>();
        after.put("USD", 1000.0);

        var report = calculator.report(before, after);

        assertThat(report.before()).isEmpty();
        assertThat(report.after()).hasSize(1);
        assertThat(report.verdict()).isEqualTo("worsens");
    }

    @Test
    void report_EmptyAfter_ReturnsReport() {
        Map<String, Double> before = new HashMap<>();
        before.put("USD", 1000.0);

        Map<String, Double> after = new HashMap<>();

        var report = calculator.report(before, after);

        assertThat(report.before()).hasSize(1);
        assertThat(report.after()).isEmpty();
        assertThat(report.verdict()).isEqualTo("improves");
    }

    // --- 진단 (FR-XR-03 · FR-FT-01, API 명세 v2 §5.3 · §5.5) ---
    // 상태 어휘는 above_threshold / within_threshold / unknown 이다.
    // v1 의 warning/safe 는 판정을 가치평가처럼 읽히게 해 §5.3 어휘로 교체했다.

    @Test
    @DisplayName("명세 §4 fixture — USD 0.6388 > balanced 0.60 → above_threshold, gap 0.0388")
    void fixture_균형항로형_기준선_초과() {
        ConcentrationCalculator.ConcentrationResult result =
                calculator.diagnose(fixtureExposure(), ConcentrationThresholdTable.BALANCED);

        assertThat(result.topCurrency()).isEqualTo("USD");
        assertThat(result.topShare()).isEqualTo(0.6388);
        assertThat(result.threshold()).isEqualTo(0.60);
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.ABOVE_THRESHOLD);
        assertThat(result.gapPp()).isEqualTo(0.0388);
        assertThat(result.totalFxAssetKrw()).isEqualTo(24_720_000L);
        assertThat(result.exposure())
                .containsEntry("USD", 0.6388)
                .containsEntry("JPY", 0.2213)
                .containsEntry("EUR", 0.1400);
    }

    @Test
    @DisplayName("주력 통화 비중이 기준선 이내면 within_threshold")
    void 기준선_이내면_within_threshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);
        assets.put("EUR", 200000L);
        assets.put("JPY", 500000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.6);

        assertThat(result.topCurrency()).isEqualTo("JPY");
        assertThat(result.topShare()).isEqualTo(0.5);
        assertThat(result.threshold()).isEqualTo(0.6);
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.WITHIN_THRESHOLD);
        assertThat(result.gapPp()).isEqualTo(-0.1);
    }

    @Test
    @DisplayName("기준선과 정확히 같으면 초과가 아니다 (경계는 within_threshold)")
    void 기준선과_같으면_within_threshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 400000L);
        assets.put("EUR", 600000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.6);

        assertThat(result.topCurrency()).isEqualTo("EUR");
        assertThat(result.topShare()).isEqualTo(0.6);
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.WITHIN_THRESHOLD);
        assertThat(result.gapPp()).isZero();
    }

    @Test
    @DisplayName("반올림 전 값으로 비교한다 — 0.60004 는 기준선 0.60 초과다")
    void 반올림_전_값으로_비교한다() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 60_004L);
        assets.put("EUR", 39_996L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.60);

        // 표시용 비중은 4자리로 접히지만 판정은 접기 전 값으로 한다.
        assertThat(result.topShare()).isEqualTo(0.6);
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.ABOVE_THRESHOLD);
    }

    @Test
    @DisplayName("성향 미측정(기준선 null)이면 unknown 이고 gap 도 없다")
    void 기준선이_null_이면_unknown() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 1_000_000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, null);

        assertThat(result.topCurrency()).isEqualTo("USD");
        assertThat(result.topShare()).isEqualTo(1.0);
        assertThat(result.threshold()).isNull();
        assertThat(result.gapPp()).isNull();
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.UNKNOWN);
    }

    @Test
    @DisplayName("단일 통화는 비중 1.0 이라 어떤 기준선도 넘는다")
    void 단일_통화는_비중_1이다() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 1_000_000L);

        ConcentrationCalculator.ConcentrationResult result = calculator.diagnose(assets, 0.5);

        assertThat(result.topShare()).isEqualTo(1.0);
        assertThat(result.status()).isEqualTo(ConcentrationCalculator.ABOVE_THRESHOLD);
    }

    @Test
    @DisplayName("외화자산이 없으면 unknown 이고 주력 통화·비중은 null 이다 (빈 상태, FR-CM-09)")
    void 외화자산이_없으면_unknown() {
        ConcentrationCalculator.ConcentrationResult empty =
                calculator.diagnose(new HashMap<>(), 0.5);

        assertThat(empty.exposure()).isEmpty();
        assertThat(empty.topCurrency()).isNull();
        assertThat(empty.topShare()).isNull();
        assertThat(empty.gapPp()).isNull();
        assertThat(empty.threshold()).isEqualTo(0.5);
        assertThat(empty.status()).isEqualTo(ConcentrationCalculator.UNKNOWN);
        assertThat(empty.totalFxAssetKrw()).isZero();

        Map<String, Long> zeros = new HashMap<>();
        zeros.put("USD", 0L);
        zeros.put("EUR", 0L);
        assertThat(calculator.diagnose(zeros, 0.5).status())
                .isEqualTo(ConcentrationCalculator.UNKNOWN);
    }

    @Test
    @DisplayName("기준선 0 과 1 도 유효한 입력이다")
    void 기준선_경계값() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 600000L);
        assets.put("EUR", 400000L);

        assertThat(calculator.diagnose(assets, 0.0).status())
                .isEqualTo(ConcentrationCalculator.ABOVE_THRESHOLD);
        assertThat(calculator.diagnose(assets, 1.0).status())
                .isEqualTo(ConcentrationCalculator.WITHIN_THRESHOLD);
    }

    @Test
    @DisplayName("집중도 진단: 음수 임계값 예외")
    void testDiagnose_NegativeThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.diagnose(assets, -0.1));
    }

    @Test
    @DisplayName("집중도 진단: 1초과 임계값 예외")
    void testDiagnose_OverThreshold() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 100000L);

        assertThrows(IllegalArgumentException.class, () ->
                calculator.diagnose(assets, 1.5));
    }

    @Test
    @DisplayName("집중도 진단: null 맵 예외")
    void testDiagnose_NullMap() {
        assertThrows(NullPointerException.class, () ->
                calculator.diagnose(null, 0.5));
    }

    /** 명세 §4 Mock fixture 의 통화별 노출 (합 24,720,000원). */
    private static Map<String, Long> fixtureExposure() {
        Map<String, Long> exposure = new LinkedHashMap<>();
        exposure.put("USD", 15_790_000L);
        exposure.put("JPY", 5_470_000L);
        exposure.put("EUR", 3_460_000L);
        return exposure;
    }

    @Test
    @DisplayName("정렬된 노출도: 내림차순")
    void testGetSortedExposure_Descending() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 300000L);
        assets.put("EUR", 200000L);
        assets.put("JPY", 500000L);

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        // LinkedHashMap이므로 삽입 순서 확인 필요 (내림차순)
        var iterator = sorted.keySet().iterator();
        String first = iterator.next();
        assertEquals("JPY", first); // 500000 (가장 큼)
    }

    @Test
    @DisplayName("정렬된 노출도: 빈 맵")
    void testGetSortedExposure_Empty() {
        Map<String, Long> assets = new HashMap<>();

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        assertEquals(0, sorted.size());
    }

    @Test
    @DisplayName("정렬된 노출도: 자산 0")
    void testGetSortedExposure_ZeroAssets() {
        Map<String, Long> assets = new HashMap<>();
        assets.put("USD", 0L);
        assets.put("EUR", 0L);

        Map<String, Double> sorted = calculator.getSortedExposure(assets);

        assertNotNull(sorted);
        assertEquals(0, sorted.size());
    }

    @Test
    @DisplayName("정렬된 노출도: null 맵 예외")
    void testGetSortedExposure_NullMap() {
        assertThrows(NullPointerException.class, () ->
                calculator.getSortedExposure(null));
    }
}
