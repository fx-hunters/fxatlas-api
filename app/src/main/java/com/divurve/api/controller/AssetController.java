package com.divurve.api.controller;

import com.divurve.api.dto.asset.DepositCreateRequest;
import com.divurve.api.dto.asset.DepositResponse;
import com.divurve.api.dto.asset.HoldingCreateRequest;
import com.divurve.api.dto.asset.HoldingResponse;
import com.divurve.api.dto.asset.HoldingUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자산(보유 종목·외화 예금) 엔드포인트 스텁 (명세 2장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Asset", description = "보유 종목·외화 예금")
public class AssetController {

    @Operation(summary = "보유 종목 목록")
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> listHoldings() {
        throw new NotImplementedException();
    }

    @Operation(summary = "종목 추가")
    @PostMapping("/holdings")
    public ApiResponse<HoldingResponse> createHolding(@RequestBody HoldingCreateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "종목 수정")
    @PutMapping("/holdings/{id}")
    public ApiResponse<HoldingResponse> updateHolding(
            @PathVariable String id,
            @RequestBody HoldingUpdateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "종목 삭제")
    @DeleteMapping("/holdings/{id}")
    public ApiResponse<Void> deleteHolding(@PathVariable String id) {
        throw new NotImplementedException();
    }

    @Operation(summary = "외화 예금 목록")
    @GetMapping("/deposits")
    public ApiResponse<List<DepositResponse>> listDeposits() {
        throw new NotImplementedException();
    }

    @Operation(summary = "외화 예금 추가")
    @PostMapping("/deposits")
    public ApiResponse<DepositResponse> createDeposit(@RequestBody DepositCreateRequest request) {
        throw new NotImplementedException();
    }
}
