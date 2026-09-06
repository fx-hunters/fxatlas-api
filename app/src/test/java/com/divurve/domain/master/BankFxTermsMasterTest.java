package com.divurve.domain.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.divurve.domain.settings.BankSpreadTable;
import org.junit.jupiter.api.Test;

/**
 * {@link BankFxTermsMaster} 단위 테스트 — 등록 여부 판정과 통화·채널 조건 조립을 검증한다.
 */
class BankFxTermsMasterTest {

    @Test
    void 등록된_은행은_contains_가_참이다() {
        assertThat(BankFxTermsMaster.contains("081")).isTrue();
    }

    @Test
    void 미등록_은행과_null_은_contains_가_거짓이다() {
        assertThat(BankFxTermsMaster.contains("999")).isFalse();
        assertThat(BankFxTermsMaster.contains(null)).isFalse();
    }

    @Test
    void 미등록_은행은_빈_조건목록을_반환한다() {
        assertThat(BankFxTermsMaster.termsOf("999")).isEmpty();
    }

    @Test
    void 등록_은행은_통화3종_채널2종의_조건을_반환한다() {
        assertThat(BankFxTermsMaster.termsOf("081"))
                .hasSize(6)
                .extracting(BankFxTermsMaster.Term::channel)
                .contains("cash", "transfer");
    }

    @Test
    void USD_현찰_스프레드는_은행_기본_스프레드와_같고_고정수수료는_0이다() {
        BankFxTermsMaster.Term usdCash = BankFxTermsMaster.termsOf("081").stream()
                .filter(t -> t.currencyCode().equals("USD") && t.channel().equals("cash"))
                .findFirst()
                .orElseThrow();

        assertThat(usdCash.listSpread()).isCloseTo(BankSpreadTable.baseSpreadRatio("081"), within(1e-9));
        assertThat(usdCash.fixedFeeKrw()).isZero();
    }

    @Test
    void 전신환은_스프레드가_현찰보다_낮고_고정수수료가_붙는다() {
        BankFxTermsMaster.Term usdTransfer = BankFxTermsMaster.termsOf("081").stream()
                .filter(t -> t.currencyCode().equals("USD") && t.channel().equals("transfer"))
                .findFirst()
                .orElseThrow();
        double base = BankSpreadTable.baseSpreadRatio("081");

        assertThat(usdTransfer.listSpread()).isCloseTo(base * 0.55, within(1e-9));
        assertThat(usdTransfer.fixedFeeKrw()).isEqualTo(10_000L);
    }

    @Test
    void 통화별_배수가_스프레드에_반영된다() {
        double base = BankSpreadTable.baseSpreadRatio("081");
        BankFxTermsMaster.Term eurCash = BankFxTermsMaster.termsOf("081").stream()
                .filter(t -> t.currencyCode().equals("EUR") && t.channel().equals("cash"))
                .findFirst()
                .orElseThrow();

        assertThat(eurCash.listSpread()).isCloseTo(base * 1.15, within(1e-9));
    }
}
