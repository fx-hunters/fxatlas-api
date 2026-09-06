package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PurchaseFxRateResolver} — 자동조회 성공/실패/폴백/KRW/JPY 정규화 케이스 커버리지 (FR-ON-04).
 */
@ExtendWith(MockitoExtension.class)
class PurchaseFxRateResolverTest {

    @Mock
    private FxRateProvider fxRateProvider;

    private PurchaseFxRateResolver resolver() {
        return new PurchaseFxRateResolver(fxRateProvider);
    }

    private final LocalDate purchasedAt = LocalDate.of(2025, 3, 10);

    @Test
    void 매입일_없으면_컨텍스트를_만들지_않는다() {
        assertThat(resolver().resolve("USD", null, null)).isNull();
        verify(fxRateProvider, never()).fetchLatest("USD_KRW");
    }

    @Test
    void KRW_는_환율_조회를_건너뛴다() {
        assertThat(resolver().resolve("KRW", purchasedAt, new BigDecimal("1"))).isNull();
        verify(fxRateProvider, never()).fetchLatest("KRW_KRW");
    }

    @Test
    void 폴백_값이_있으면_수동_출처로_기록한다() {
        PurchaseFxRate rate = resolver().resolve("USD", purchasedAt, new BigDecimal("1345.5"));

        assertThat(rate.rateKrw()).isEqualByComparingTo("1345.5");
        assertThat(rate.source()).isEqualTo(PurchaseFxRateResolver.SOURCE_MANUAL);
        assertThat(rate.asOf()).isEqualTo(purchasedAt);
        verify(fxRateProvider, never()).fetchLatest("USD_KRW");
    }

    @Test
    void 자동조회_성공_시_provider_스냅샷을_그대로_전달한다() {
        LocalDate asOf = purchasedAt.minusDays(1);
        when(fxRateProvider.fetchLatest("USD_KRW"))
                .thenReturn(new RateSnapshot(
                        "USD_KRW", new BigDecimal("1350.42"), asOf, "ECOS", Instant.parse("2025-03-10T00:00:00Z")));

        PurchaseFxRate rate = resolver().resolve("USD", purchasedAt, null);

        assertThat(rate.rateKrw()).isEqualByComparingTo("1350.42");
        assertThat(rate.source()).isEqualTo("ECOS");
        assertThat(rate.asOf()).isEqualTo(asOf);
    }

    @Test
    void JPY_는_원_100엔_응답을_원_1엔으로_정규화한다() {
        LocalDate asOf = purchasedAt.minusDays(1);
        when(fxRateProvider.fetchLatest("JPY_KRW"))
                .thenReturn(new RateSnapshot(
                        "JPY_KRW", new BigDecimal("930.5"), asOf, "ECOS", Instant.EPOCH));

        PurchaseFxRate rate = resolver().resolve("JPY", purchasedAt, null);

        assertThat(rate.rateKrw()).isEqualByComparingTo("9.305");
    }

    @Test
    void 자동조회_실패시_폴백_없으면_400_에러코드로_표면화한다() {
        when(fxRateProvider.fetchLatest("USD_KRW")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> resolver().resolve("USD", purchasedAt, null))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(PurchaseFxRateResolver.ERROR_LOOKUP_FAILED);
                    assertThat(ex.getField()).isEqualTo("purchase_fx_rate_krw");
                    assertThat(ex.getDetail()).isEqualTo("boom");
                });
    }
}
