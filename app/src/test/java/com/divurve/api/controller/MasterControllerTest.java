package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.master.CurrencyListResponse;
import com.divurve.api.dto.master.FxTermsResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.master.BankFxTermsMaster;
import com.divurve.domain.master.CurrencyMaster;
import com.divurve.domain.master.MasterDataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MasterController} 매핑 검증 — 도메인 값 → DTO 변환과 data/meta 래핑.
 */
@ExtendWith(MockitoExtension.class)
class MasterControllerTest {

    @Mock
    private MasterDataService masterDataService;

    private MasterController controller() {
        return new MasterController(masterDataService);
    }

    @Test
    void listCurrencies_는_통화_표시규칙을_data_meta로_래핑한다() {
        when(masterDataService.listCurrencies()).thenReturn(List.of(
                new CurrencyMaster.Currency("USD", 2, 1, "base", "currency-usd")));

        ApiResponse<CurrencyListResponse> response = controller().listCurrencies();

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().currencies()).singleElement().satisfies(c -> {
            assertThat(c.currencyCode()).isEqualTo("USD");
            assertThat(c.minorUnits()).isEqualTo(2);
            assertThat(c.quoteUnit()).isEqualTo(1);
            assertThat(c.usdSide()).isEqualTo("base");
            assertThat(c.colorToken()).isEqualTo("currency-usd");
        });
    }

    @Test
    void getFxTerms_는_은행_환전조건을_data_meta로_래핑한다() {
        when(masterDataService.getFxTerms("081")).thenReturn(new MasterDataService.FxTerms(
                "081", List.of(new BankFxTermsMaster.Term("USD", "cash", 0.0165, 0L))));

        ApiResponse<FxTermsResponse> response = controller().getFxTerms("081");

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().bankCode()).isEqualTo("081");
        assertThat(response.data().terms()).singleElement().satisfies(t -> {
            assertThat(t.currencyCode()).isEqualTo("USD");
            assertThat(t.channel()).isEqualTo("cash");
            assertThat(t.listSpread()).isEqualTo(0.0165);
            assertThat(t.fixedFeeKrw()).isZero();
        });
    }
}
