package com.divurve.api.controller;

import com.divurve.api.dto.master.CurrencyListResponse;
import com.divurve.api.dto.master.FxTermsResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.master.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마스터(통화·은행 조건) 엔드포인트 (명세 2장, 이슈 #11). 지원 통화 표시 규칙과 은행별 환전 조건을 조회한다.
 * 이 값들은 M2 플래너 비용계산(FR-RT-11)의 선행 입력이다 — 여기서는 조회·매핑만 한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Master", description = "지원 통화·은행 환전 조건")
public class MasterController {

    private final MasterDataService masterDataService;

    public MasterController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @Operation(summary = "지원 통화와 표시 규칙")
    @GetMapping("/currencies")
    public ApiResponse<CurrencyListResponse> listCurrencies() {
        return ApiResponse.of(toCurrencyListResponse());
    }

    @Operation(summary = "은행·통화·채널별 환전 조건")
    @GetMapping("/banks/{bank_code}/fx-terms")
    public ApiResponse<FxTermsResponse> getFxTerms(@PathVariable("bank_code") String bankCode) {
        return ApiResponse.of(toFxTermsResponse(masterDataService.getFxTerms(bankCode)));
    }

    private CurrencyListResponse toCurrencyListResponse() {
        return new CurrencyListResponse(masterDataService.listCurrencies().stream()
                .map(c -> new CurrencyListResponse.Currency(
                        c.currencyCode(), c.minorUnits(), c.quoteUnit(), c.usdSide(), c.colorToken()))
                .toList());
    }

    private FxTermsResponse toFxTermsResponse(MasterDataService.FxTerms fxTerms) {
        return new FxTermsResponse(fxTerms.bankCode(), fxTerms.terms().stream()
                .map(t -> new FxTermsResponse.Term(
                        t.currencyCode(), t.channel(), t.listSpread(), t.fixedFeeKrw()))
                .toList());
    }
}
