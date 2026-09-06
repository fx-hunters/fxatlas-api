package com.divurve.engine.volatility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.divurve.engine.volatility.MarketChecks.Check;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MarketChecks — /market/regime 판정 근거 (명세 §5.10)")
class MarketChecksTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 1);

    private MarketChecks checks;

    @BeforeEach
    void setUp() {
        checks = new MarketChecks();
    }

    @Nested
    @DisplayName("data_freshness")
    class DataFreshness {

        @Test
        @DisplayName("당일 갱신은 통과하고 사유가 비어 있다")
        void 당일_갱신() {
            Check check = checks.dataFreshness(AS_OF, AS_OF);

            assertEquals(MarketChecks.KEY_DATA_FRESHNESS, check.key());
            assertTrue(check.passed());
            assertNull(check.detail());
        }

        @Test
        @DisplayName("경계 — 2일 경과는 통과, 3일 경과는 실패")
        void 경계값() {
            assertTrue(checks.dataFreshness(AS_OF.minusDays(2), AS_OF).passed());

            Check failed = checks.dataFreshness(AS_OF.minusDays(3), AS_OF);
            assertFalse(failed.passed());
            assertEquals("마지막 갱신 이후 3일 경과했습니다.", failed.detail());
        }

        @Test
        @DisplayName("갱신 이력이 없으면 실패")
        void 이력_없음() {
            Check check = checks.dataFreshness(null, AS_OF);

            assertFalse(check.passed());
            assertEquals("시장 데이터 갱신 이력이 없습니다.", check.detail());
        }

        @Test
        @DisplayName("기준일을 인자로 받는다 — 현재 시각에 의존하지 않는다(v1 SafeModeEvaluator 의 재현 불가 결함)")
        void 기준일_필수() {
            assertThrows(NullPointerException.class, () -> checks.dataFreshness(AS_OF, null));
        }
    }

    @Nested
    @DisplayName("source_divergence")
    class SourceDivergence {

        @Test
        @DisplayName("경계 — 4.99% 차이는 통과, 5% 차이는 실패")
        void 경계값() {
            assertTrue(checks.sourceDivergence(
                new BigDecimal("1000.00"), new BigDecimal("1049.90")).passed());

            Check failed = checks.sourceDivergence(
                new BigDecimal("1000.00"), new BigDecimal("1050.00"));
            assertEquals(MarketChecks.KEY_SOURCE_DIVERGENCE, failed.key());
            assertFalse(failed.passed());
            assertEquals("출처 간 환율 차이가 5.00%입니다.", failed.detail());
        }

        @Test
        @DisplayName("순서가 뒤바뀌어도 같은 판정 — 낮은 쪽을 분모로 쓴다")
        void 인자_순서_무관() {
            Check a = checks.sourceDivergence(new BigDecimal("1050.00"), new BigDecimal("1000.00"));
            Check b = checks.sourceDivergence(new BigDecimal("1000.00"), new BigDecimal("1050.00"));

            assertEquals(a, b);
        }

        @Test
        @DisplayName("비교 출처가 없으면 판정 대상이 아니므로 통과 — 없는 근거를 만들지 않는다")
        void 단일_출처() {
            assertTrue(checks.sourceDivergence(null, new BigDecimal("1000.00")).passed());
            assertTrue(checks.sourceDivergence(new BigDecimal("1000.00"), null).passed());
            assertNull(checks.sourceDivergence(null, null).detail());
        }

        @Test
        @DisplayName("0 이하 환율은 예외 — 0 으로 나누는 대신 입력을 거부한다")
        void 비정상_환율() {
            assertThrows(IllegalArgumentException.class, () -> checks.sourceDivergence(
                BigDecimal.ZERO, new BigDecimal("1000.00")));
            assertThrows(IllegalArgumentException.class, () -> checks.sourceDivergence(
                new BigDecimal("1000.00"), new BigDecimal("-1")));
        }
    }

    @Nested
    @DisplayName("vol_percentile")
    class VolPercentile {

        @Test
        @DisplayName("명세 §5.10 예시 재현 — USDKRW 0.72 는 실패하며 상위 28% 구간으로 설명한다")
        void 명세_예시_재현() {
            Check check = checks.volPercentile("USDKRW", 0.72);

            assertEquals(MarketChecks.KEY_VOL_PERCENTILE, check.key());
            assertFalse(check.passed());
            assertEquals("USDKRW 30일 변동성이 5년 상위 28% 구간입니다.", check.detail());
        }

        @Test
        @DisplayName("경계 — elevated 시작점(0.70) 직전은 통과, 0.70 은 실패")
        void 경계값() {
            Check passed = checks.volPercentile("USDJPY", 0.6999);
            assertTrue(passed.passed());
            assertNull(passed.detail());

            assertFalse(checks.volPercentile(
                "USDJPY", RegimeClassifier.ELEVATED_MIN_PERCENTILE).passed());
        }

        @Test
        @DisplayName("통화쌍은 필수")
        void 통화쌍_필수() {
            assertThrows(NullPointerException.class, () -> checks.volPercentile(null, 0.5));
        }

        @Test
        @DisplayName("0~1 비율이 아니면 예외")
        void 범위_검증() {
            assertThrows(IllegalArgumentException.class, () -> checks.volPercentile("USDKRW", -0.01));
            assertThrows(IllegalArgumentException.class, () -> checks.volPercentile("USDKRW", 72.0));
            assertThrows(IllegalArgumentException.class,
                () -> checks.volPercentile("USDKRW", Double.NaN));
        }
    }
}
