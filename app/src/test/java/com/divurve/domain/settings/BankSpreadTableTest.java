package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link BankSpreadTable} 단위 테스트 — 등록 은행 조회, 미등록/미지정은 기본값.
 */
class BankSpreadTableTest {

    @Test
    void 등록된_은행코드는_해당_기본_스프레드를_반환한다() {
        assertThat(BankSpreadTable.baseSpreadRatio("081")).isEqualTo(0.0165);
    }

    @Test
    void 미등록_은행코드는_기본값을_반환한다() {
        assertThat(BankSpreadTable.baseSpreadRatio("999"))
                .isEqualTo(BankSpreadTable.DEFAULT_BASE_SPREAD_RATIO);
    }

    @Test
    void null_은행코드는_기본값을_반환한다() {
        assertThat(BankSpreadTable.baseSpreadRatio(null))
                .isEqualTo(BankSpreadTable.DEFAULT_BASE_SPREAD_RATIO);
    }

    @Test
    void isRegistered_는_등록_은행만_참이다() {
        assertThat(BankSpreadTable.isRegistered("081")).isTrue();
        assertThat(BankSpreadTable.isRegistered("999")).isFalse();
        assertThat(BankSpreadTable.isRegistered(null)).isFalse();
    }
}
