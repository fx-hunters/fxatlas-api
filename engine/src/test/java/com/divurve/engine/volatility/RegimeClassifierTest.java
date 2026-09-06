package com.divurve.engine.volatility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("RegimeClassifier — 변동성 백분위 → 국면 4종")
class RegimeClassifierTest {

    private RegimeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RegimeClassifier();
    }

    @Nested
    @DisplayName("명세 예시값 재현 (API v2 §5.7 · §5.10)")
    class SpecExamples {

        @ParameterizedTest(name = "vol_percentile_5y={0} → {1}")
        @CsvSource({
            "0.22, calm",      // §5.10 EURUSD
            "0.41, normal",    // §5.10 USDJPY
            "0.72, elevated",  // §5.7 · §5.10 USDKRW
        })
        void 명세에_적힌_국면과_같다(double percentile, String expectedCode) {
            assertEquals(expectedCode, classifier.classify(percentile).code());
        }
    }

    @Nested
    @DisplayName("경계값 — 하한 포함 · 상한 미포함")
    class Boundaries {

        @Test
        @DisplayName("0.25 직전은 calm, 0.25 는 normal")
        void normal_경계() {
            assertEquals(Regime.CALM, classifier.classify(0.2499));
            assertEquals(Regime.NORMAL, classifier.classify(RegimeClassifier.NORMAL_MIN_PERCENTILE));
        }

        @Test
        @DisplayName("0.70 직전은 normal, 0.70 은 elevated")
        void elevated_경계() {
            assertEquals(Regime.NORMAL, classifier.classify(0.6999));
            assertEquals(Regime.ELEVATED, classifier.classify(RegimeClassifier.ELEVATED_MIN_PERCENTILE));
        }

        @Test
        @DisplayName("0.90 직전은 elevated, 0.90 은 stress")
        void stress_경계() {
            assertEquals(Regime.ELEVATED, classifier.classify(0.8999));
            assertEquals(Regime.STRESS, classifier.classify(RegimeClassifier.STRESS_MIN_PERCENTILE));
        }

        @Test
        @DisplayName("정의역 양 끝 0.0 · 1.0 도 허용된다")
        void 양_끝값() {
            assertEquals(Regime.CALM, classifier.classify(0.0));
            assertEquals(Regime.STRESS, classifier.classify(1.0));
        }
    }

    @Nested
    @DisplayName("입력 검증 — 0~100 정수 단위 혼동을 조용히 흡수하지 않는다")
    class Validation {

        @Test
        @DisplayName("0 미만은 예외")
        void 음수() {
            assertThrows(IllegalArgumentException.class, () -> classifier.classify(-0.01));
        }

        @Test
        @DisplayName("1 초과(0~100 정수를 넘긴 경우)는 예외")
        void 범위_초과() {
            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> classifier.classify(72.0));
            assertTrue(ex.getMessage().contains("0~1"));
        }

        @Test
        @DisplayName("NaN 은 예외 — 비교 연산이 전부 false 라 stress 로 새는 것을 막는다")
        void nan() {
            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> classifier.classify(Double.NaN));
            assertTrue(ex.getMessage().contains("NaN"));
        }
    }

    @Nested
    @DisplayName("Regime 코드 — ERD fx_stats.regime ENUM 과 1:1")
    class RegimeCodes {

        @Test
        void 네_국면의_코드가_ERD_ENUM_과_같다() {
            assertEquals("calm", Regime.CALM.code());
            assertEquals("normal", Regime.NORMAL.code());
            assertEquals("elevated", Regime.ELEVATED.code());
            assertEquals("stress", Regime.STRESS.code());
        }

        @Test
        @DisplayName("선언 순서가 심각도 순서다")
        void 심각도_순서() {
            assertTrue(Regime.CALM.ordinal() < Regime.NORMAL.ordinal());
            assertTrue(Regime.NORMAL.ordinal() < Regime.ELEVATED.ordinal());
            assertTrue(Regime.ELEVATED.ordinal() < Regime.STRESS.ordinal());
        }
    }
}
