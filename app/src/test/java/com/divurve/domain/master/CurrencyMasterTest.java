package com.divurve.domain.master;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link CurrencyMaster} 단위 테스트 — 표시 순서·표시 규칙 값이 명세대로인지 확인한다.
 */
class CurrencyMasterTest {

    @Test
    void 지원_통화를_명세_표시_순서대로_반환한다() {
        assertThat(CurrencyMaster.all())
                .extracting(CurrencyMaster.Currency::currencyCode)
                .containsExactly("USD", "EUR", "JPY", "GBP", "CNY");
    }

    @Test
    void JPY_는_소수0자리_100단위_호가로_표시한다() {
        CurrencyMaster.Currency jpy = CurrencyMaster.all().stream()
                .filter(c -> c.currencyCode().equals("JPY"))
                .findFirst()
                .orElseThrow();

        assertThat(jpy.minorUnits()).isZero();
        assertThat(jpy.quoteUnit()).isEqualTo(100);
        assertThat(jpy.usdSide()).isEqualTo("base");
        assertThat(jpy.colorToken()).isEqualTo("currency-jpy");
    }

    @Test
    void EUR_는_USD페어에서_quote_측이다() {
        CurrencyMaster.Currency eur = CurrencyMaster.all().stream()
                .filter(c -> c.currencyCode().equals("EUR"))
                .findFirst()
                .orElseThrow();

        assertThat(eur.minorUnits()).isEqualTo(2);
        assertThat(eur.quoteUnit()).isEqualTo(1);
        assertThat(eur.usdSide()).isEqualTo("quote");
    }
}
