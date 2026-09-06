package com.divurve.engine.volatility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.divurve.engine.volatility.RegimeBadgeMapper.Badge;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("RegimeBadgeMapper — 명세 §2 상태 어휘 매핑")
class RegimeBadgeMapperTest {

    private RegimeBadgeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RegimeBadgeMapper();
    }

    @Nested
    @DisplayName("국면 4종 → 배지 3종 고정 매핑표")
    class FixedMapping {

        @ParameterizedTest(name = "{0} → {1} ({2})")
        @CsvSource({
            "CALM,     normal,    정상",
            "NORMAL,   normal,    정상",
            "ELEVATED, caution,   주의",
            "STRESS,   turbulent, 급변",
        })
        void 명세_표_그대로_매핑한다(Regime regime, String expectedCode, String expectedLabel) {
            Badge badge = mapper.toBadge(regime);

            assertEquals(expectedCode, badge.code());
            assertEquals(expectedLabel, badge.label());
        }

        @Test
        @DisplayName("calm 과 normal 은 같은 배지로 합쳐진다 — 4종이 3종이 되는 유일한 지점")
        void calm_과_normal_은_같은_배지() {
            assertEquals(mapper.toBadge(Regime.CALM), mapper.toBadge(Regime.NORMAL));
            assertEquals(Badge.NORMAL, mapper.toBadge(Regime.CALM));
        }

        @Test
        void null_국면은_예외() {
            assertThrows(NullPointerException.class, () -> mapper.toBadge(null));
        }
    }

    @Nested
    @DisplayName("worstOf — 통화쌍별 국면 중 대표값 (§5.10)")
    class WorstOf {

        @Test
        @DisplayName("명세 §5.10 예시: elevated · normal · calm → elevated(= caution 배지)")
        void 명세_예시_재현() {
            Regime worst = mapper.worstOf(List.of(Regime.ELEVATED, Regime.NORMAL, Regime.CALM));

            assertEquals(Regime.ELEVATED, worst);
            assertEquals("caution", mapper.toBadge(worst).code());
        }

        @Test
        @DisplayName("하나라도 stress 면 stress — 가장 안전한 쪽이 아니라 가장 심각한 쪽")
        void 하나라도_급변이면_급변() {
            assertEquals(Regime.STRESS, mapper.worstOf(List.of(Regime.CALM, Regime.STRESS)));
        }

        @Test
        @DisplayName("전부 calm 이면 calm")
        void 전부_평온() {
            assertEquals(Regime.CALM, mapper.worstOf(List.of(Regime.CALM, Regime.CALM)));
        }

        @Test
        @DisplayName("원소 하나여도 그대로 돌려준다")
        void 단일_원소() {
            assertEquals(Regime.NORMAL, mapper.worstOf(List.of(Regime.NORMAL)));
        }

        @Test
        void 빈_목록은_예외() {
            assertThrows(IllegalArgumentException.class, () -> mapper.worstOf(List.of()));
        }

        @Test
        void null_목록은_예외() {
            assertThrows(NullPointerException.class, () -> mapper.worstOf(null));
        }

        @Test
        void 원소가_null_이면_예외() {
            List<Regime> withNull = Arrays.asList(Regime.CALM, null);
            assertThrows(NullPointerException.class, () -> mapper.worstOf(withNull));
        }
    }
}
