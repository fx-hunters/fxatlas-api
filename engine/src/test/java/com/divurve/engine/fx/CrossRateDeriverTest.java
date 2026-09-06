package com.divurve.engine.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.engine.fx.CrossRateDeriver.DatedRate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CrossRateDeriver} — 삼각 유도 환율 (이슈 #57).
 *
 * <p>ECOS 가 원화 크로스만 고시하므로 {@code USDJPY}·{@code EURUSD} 는 비로 만든다.
 */
@DisplayName("CrossRateDeriver")
class CrossRateDeriverTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);

    private final CrossRateDeriver deriver = new CrossRateDeriver();

    @Test
    @DisplayName("USD/JPY = (USD/KRW) ÷ (JPY/KRW) — 둘 다 1단위 기준")
    void 두_원화_크로스의_비로_유도한다() {
        // USD/KRW 1,382.40 · JPY/KRW 9.216 (= 원/100엔 921.60 을 1엔 기준으로 접은 값)
        List<DatedRate> usdKrw = List.of(new DatedRate(D1, 1382.40));
        List<DatedRate> jpyKrw = List.of(new DatedRate(D1, 9.216));

        List<DatedRate> usdJpy = deriver.derive(usdKrw, jpyKrw);

        assertThat(usdJpy).hasSize(1);
        assertThat(usdJpy.get(0).date()).isEqualTo(D1);
        assertThat(usdJpy.get(0).rate()).isEqualTo(1382.40 / 9.216);
    }

    @Test
    @DisplayName("한쪽에만 있는 날짜는 버린다 — 없는 관측을 메우지 않는다 (FR-CM-10)")
    void 교집합_날짜만_남긴다() {
        List<DatedRate> base = List.of(
                new DatedRate(D1, 1380.0), new DatedRate(D2, 1390.0), new DatedRate(D3, 1400.0));
        List<DatedRate> quote = List.of(new DatedRate(D1, 9.2), new DatedRate(D3, 9.4));

        assertThat(deriver.derive(base, quote))
                .extracting(DatedRate::date)
                .containsExactly(D1, D3);
    }

    @Test
    @DisplayName("결과는 날짜 오름차순이다 — 수익률 계산이 순서에 의존한다")
    void 날짜_오름차순으로_돌려준다() {
        List<DatedRate> base = List.of(
                new DatedRate(D3, 1400.0), new DatedRate(D1, 1380.0), new DatedRate(D2, 1390.0));
        List<DatedRate> quote = List.of(
                new DatedRate(D1, 9.2), new DatedRate(D2, 9.3), new DatedRate(D3, 9.4));

        assertThat(deriver.derive(base, quote))
                .extracting(DatedRate::date)
                .containsExactly(D1, D2, D3);
    }

    @Test
    @DisplayName("분모가 0 이하인 날은 계산할 수 없으므로 제외한다")
    void 분모가_양수가_아니면_제외한다() {
        List<DatedRate> base = List.of(new DatedRate(D1, 1380.0), new DatedRate(D2, 1390.0));
        List<DatedRate> quote = List.of(new DatedRate(D1, 0.0), new DatedRate(D2, -9.2));

        assertThat(deriver.derive(base, quote)).isEmpty();
    }

    @Test
    @DisplayName("겹치는 날짜가 없으면 빈 결과")
    void 겹치는_날짜가_없으면_빈_결과다() {
        assertThat(deriver.derive(
                List.of(new DatedRate(D1, 1380.0)),
                List.of(new DatedRate(D2, 9.2)))).isEmpty();
    }

    @Test
    @DisplayName("빈 입력도 빈 결과")
    void 빈_입력은_빈_결과다() {
        assertThat(deriver.derive(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("null 입력은 거부한다")
    void null_입력은_거부한다() {
        assertThatThrownBy(() -> deriver.derive(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseKrw");
        assertThatThrownBy(() -> deriver.derive(List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("quoteKrw");
    }
}
