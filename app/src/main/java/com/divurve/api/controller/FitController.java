package com.divurve.api.controller;

import com.divurve.api.dto.fit.ConcentrationResponse;
import com.divurve.api.dto.fit.SimulateRequest;
import com.divurve.api.dto.fit.SimulateResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fit(집중도·분산효과) 엔드포인트 스텁 (명세 2·3.8장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/fit")
@Tag(name = "Fit", description = "집중도 진단·분산효과 시뮬레이션")
public class FitController {

    @Operation(summary = "집중도 진단")
    @GetMapping("/concentration")
    public ApiResponse<ConcentrationResponse> getConcentration() {
        throw new NotImplementedException();
    }

    @Operation(summary = "분산효과 시뮬레이션")
    @PostMapping("/simulate")
    public ApiResponse<SimulateResponse> simulate(@RequestBody SimulateRequest request) {
        throw new NotImplementedException();
    }
}
