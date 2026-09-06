package com.divurve.domain.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

/**
 * {@link MasterDataService} 단위 테스트 — 통화 목록 조회와 은행 환전 조건 조회(미등록 은행 404).
 */
class MasterDataServiceTest {

    private final MasterDataService service = new MasterDataService();

    @Test
    void listCurrencies_는_통화_표시규칙_목록을_반환한다() {
        assertThat(service.listCurrencies())
                .extracting(CurrencyMaster.Currency::currencyCode)
                .containsExactly("USD", "EUR", "JPY", "GBP", "CNY");
    }

    @Test
    void getFxTerms_는_은행코드와_조건목록을_담아_반환한다() {
        MasterDataService.FxTerms result = service.getFxTerms("081");

        assertThat(result.bankCode()).isEqualTo("081");
        assertThat(result.terms()).hasSize(6);
    }

    @Test
    void getFxTerms_는_미등록_은행이면_NotFound_를_던진다() {
        assertThatThrownBy(() -> service.getFxTerms("999"))
                .isInstanceOf(NotFoundException.class);
    }
}
