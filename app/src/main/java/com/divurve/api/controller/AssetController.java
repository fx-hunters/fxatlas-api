package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.asset.DepositCreateRequest;
import com.divurve.api.dto.asset.DepositResponse;
import com.divurve.api.dto.asset.HoldingCreateRequest;
import com.divurve.api.dto.asset.HoldingResponse;
import com.divurve.api.dto.asset.HoldingUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자산(보유 종목·외화 예금) 엔드포인트 (이슈 #13, FR-XR-10, FR-ON-04).
 * 요청 주체는 {@link CurrentUser} 로 해석해 소유자 격리(NFR-SE-03)를 강제한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Asset", description = "보유 종목·외화 예금")
public class AssetController {

    private final HoldingService holdingService;
    private final DepositService depositService;

    public AssetController(HoldingService holdingService, DepositService depositService) {
        this.holdingService = holdingService;
        this.depositService = depositService;
    }

    @Operation(summary = "보유 종목 목록")
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> listHoldings(@CurrentUser UUID userId) {
        List<HoldingResponse> data = holdingService.list(userId).stream()
                .map(AssetController::toHoldingResponse)
                .toList();
        return ApiResponse.of(data);
    }

    @Operation(summary = "종목 추가", description = "매입일을 넘기면 서버가 매입 시점 환율을 자동 조회한다(FR-ON-04).")
    @PostMapping("/holdings")
    public ApiResponse<HoldingResponse> createHolding(
            @CurrentUser UUID userId,
            @RequestBody HoldingCreateRequest request) {
        Holding created = holdingService.create(
                userId,
                request.ticker(),
                request.currencyCode(),
                request.quantity(),
                request.avgPrice(),
                request.purchasedAt(),
                request.purchaseFxRateKrw());
        return ApiResponse.of(toHoldingResponse(created));
    }

    @Operation(summary = "종목 수정", description = "수량·평균단가만 수정할 수 있다. 매입 환율 근거는 유지된다.")
    @PutMapping("/holdings/{id}")
    public ApiResponse<HoldingResponse> updateHolding(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody HoldingUpdateRequest request) {
        Holding updated = holdingService.update(
                userId, UUID.fromString(id), request.quantity(), request.avgPrice());
        return ApiResponse.of(toHoldingResponse(updated));
    }

    @Operation(summary = "종목 삭제")
    @DeleteMapping("/holdings/{id}")
    public ApiResponse<Void> deleteHolding(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        holdingService.delete(userId, UUID.fromString(id));
        return ApiResponse.of(null);
    }

    @Operation(summary = "외화 예금 목록")
    @GetMapping("/deposits")
    public ApiResponse<List<DepositResponse>> listDeposits(@CurrentUser UUID userId) {
        List<DepositResponse> data = depositService.list(userId).stream()
                .map(AssetController::toDepositResponse)
                .toList();
        return ApiResponse.of(data);
    }

    @Operation(summary = "외화 예금 추가", description = "매입일을 넘기면 서버가 예치 시점 환율을 자동 조회한다(FR-ON-04).")
    @PostMapping("/deposits")
    public ApiResponse<DepositResponse> createDeposit(
            @CurrentUser UUID userId,
            @RequestBody DepositCreateRequest request) {
        Deposit created = depositService.create(
                userId,
                request.currencyCode(),
                request.amount(),
                request.purchasedAt(),
                request.purchaseFxRateKrw());
        return ApiResponse.of(toDepositResponse(created));
    }

    private static HoldingResponse toHoldingResponse(Holding h) {
        return new HoldingResponse(
                h.getId().toString(),
                h.getTicker(),
                h.getCurrencyCode(),
                h.getQuantity(),
                h.getAvgPrice(),
                h.getPurchasedAt(),
                h.getPurchaseFxRateKrw(),
                h.getPurchaseFxRateSource(),
                h.getPurchaseFxRateAsOf());
    }

    private static DepositResponse toDepositResponse(Deposit d) {
        return new DepositResponse(
                d.getId().toString(),
                d.getCurrencyCode(),
                d.getAmount(),
                d.getPurchasedAt(),
                d.getPurchaseFxRateKrw(),
                d.getPurchaseFxRateSource(),
                d.getPurchaseFxRateAsOf());
    }
}
