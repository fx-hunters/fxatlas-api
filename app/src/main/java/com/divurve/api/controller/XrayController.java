package com.divurve.api.controller;

import com.divurve.api.dto.xray.AttributionResponse;
import com.divurve.api.dto.xray.StressRequest;
import com.divurve.api.dto.xray.StressResponse;
import com.divurve.api.dto.xray.XrayResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 진단(X-ray) 엔드포인트 스텁 (명세 2·3.3·3.4장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/xray")
@Tag(name = "X-ray", description = "통화 노출·손익 분해·스트레스")
public class XrayController {

    @Operation(summary = "통화 노출·외화 비중·민감도")
    @GetMapping
    public ApiResponse<XrayResponse> getXray() {
        throw new NotImplementedException();
    }

    @Operation(summary = "손익 분해")
    @GetMapping("/attribution")
    public ApiResponse<AttributionResponse> getAttribution(
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) String mode) {
        throw new NotImplementedException();
    }

    @Operation(summary = "스트레스 시나리오 적용")
    @PostMapping("/stress")
    public ApiResponse<StressResponse> applyStress(@RequestBody StressRequest request) {
        throw new NotImplementedException();
    }
}
