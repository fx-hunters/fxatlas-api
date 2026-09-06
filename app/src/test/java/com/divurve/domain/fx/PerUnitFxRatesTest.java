package com.divurve.domain.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.engine.weight.QuoteUnitNormalizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PerUnitFxRates} — 통화 환율 조회의 단일 창구 (이슈 #57).
 *
 * <p>이 조회가 세 서비스에 복붙돼 있었고 사본마다 실패 처리가 달라, 같은 GBP 보유 상태에서
 * {@code /xray} 는 200, {@code /stress/runs} 는 400 이었다. 여기 하나로 모은 뒤의 계약을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerUnitFxRates")
class PerUnitFxRatesTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 1);
    private static final Instant FETCHED_AT = Instant.parse("2026-09-01T15:30:00Z");

    @Mock
    private FxRateProvider fxRateProvider;

    private PerUnitFxRates rates;

    @BeforeEach
    void setUp() {
        rates = new PerUnitFxRates(fxRateProvider, new QuoteUnitNormalizer());
    }

    private void givenRate(String pairCode, String rate) {
        when(fxRateProvider.fetchLatest(pairCode))
                .thenReturn(new RateSnapshot(pairCode, new BigDecimal(rate), AS_OF, "ECOS", FETCHED_AT));
    }

    @Test
    @DisplayName("통화코드에 _KRW 를 붙여 조회한다")
    void 원화_크로스로_조회한다() {
        givenRate("USD_KRW", "1382.40");

        assertThat(rates.require("USD")).isEqualByComparingTo("1382.40");
    }

    @Test
    @DisplayName("JPY 는 원/100엔 고시를 1엔 기준으로 접는다")
    void JPY는_100엔_고시를_접는다() {
        givenRate("JPY_KRW", "921.60");

        assertThat(rates.require("JPY")).isEqualByComparingTo("9.216");
    }

    @Test
    @DisplayName("require 는 조회 실패를 그대로 올린다 — 환율이 없으면 계산이 성립하지 않는 자리용")
    void require는_예외를_전파한다() {
        when(fxRateProvider.fetchLatest("GBP_KRW"))
                .thenThrow(new IllegalArgumentException("Unsupported pairCode for ECOS: GBP_KRW"));

        assertThatThrownBy(() -> rates.require("GBP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("require 는 결과가 null 이어도 조용히 통과시키지 않는다")
    void require는_null_결과를_거부한다() {
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(null);

        assertThatThrownBy(() -> rates.require("USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("find 는 조회 실패를 빈 값으로 바꾼다 — 통화 하나가 화면 전체를 막지 않는다 (FR-SF-01)")
    void find는_실패를_빈_값으로_바꾼다() {
        when(fxRateProvider.fetchLatest("GBP_KRW"))
                .thenThrow(new IllegalArgumentException("Unsupported pairCode for ECOS: GBP_KRW"));

        assertThat(rates.find("GBP")).isEmpty();
    }

    @Test
    @DisplayName("find 는 성공하면 require 와 같은 값을 준다")
    void find는_성공하면_같은_값이다() {
        givenRate("USD_KRW", "1382.40");

        assertThat(rates.find("USD")).hasValueSatisfying(
                rate -> assertThat(rate).isEqualByComparingTo("1382.40"));
    }

    @Test
    @DisplayName("통화코드는 필수다")
    void 통화코드는_필수다() {
        assertThatThrownBy(() -> rates.require(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currencyCode");
    }

    @Test
    @DisplayName("생성자는 null 협력자를 거부한다")
    void null_협력자를_거부한다() {
        assertThatThrownBy(() -> new PerUnitFxRates(null, new QuoteUnitNormalizer()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PerUnitFxRates(fxRateProvider, null))
                .isInstanceOf(NullPointerException.class);
    }
}
