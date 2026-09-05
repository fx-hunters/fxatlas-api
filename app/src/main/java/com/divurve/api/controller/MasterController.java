package com.divurve.api.controller;

import com.divurve.api.dto.master.CurrencyListResponse;
import com.divurve.api.dto.master.FxTermsResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마스터(통화·은행 조건) 엔드포인트 스텁 (명세 2장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Master", description = "지원 통화·은행 환전 조건")
public class MasterController {

    @Operation(summary = "지원 통화와 표시 규칙")
    @GetMapping("/currencies")
    public ApiResponse<CurrencyListResponse> listCurrencies() {
        throw new NotImplementedException();
    }

    @Operation(summary = "은행·통화·채널별 환전 조건")
    @GetMapping("/banks/{bank_code}/fx-terms")
    public ApiResponse<FxTermsResponse> getFxTerms(@PathVariable("bank_code") String bankCode) {
        throw new NotImplementedException();
    }
}
