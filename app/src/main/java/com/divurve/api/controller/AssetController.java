package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.asset.DepositCreateRequest;
import com.divurve.api.dto.asset.DepositResponse;
import com.divurve.api.dto.asset.DepositUpdateRequest;
import com.divurve.api.dto.asset.HoldingCreateRequest;
import com.divurve.api.dto.asset.HoldingResponse;
import com.divurve.api.dto.asset.HoldingUpdateRequest;
import com.divurve.api.dto.asset.KrwAssetCreateRequest;
import com.divurve.api.dto.asset.KrwAssetResponse;
import com.divurve.api.dto.asset.KrwAssetUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.holding.KrwAssetService;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.KrwAsset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 자산 엔드포인트 — 보유 종목 · 외화 예금 · <b>원화 자산</b> (API 명세 v2 §3 FR-XR-07).
 * 요청 주체는 {@link CurrentUser} 로 해석해 소유자 격리(NFR-SE-03)를 강제한다.
 *
 * <p>v2 에서 추가된 것: {@code PUT/DELETE /deposits/:id} 와 {@code /krw-assets} 4종.
 * 원화 자산은 <b>외화 비중의 분모</b>다 — v1 에는 입력 경로 자체가 없어 {@code GET /xray} 의
 * {@code total_asset_krw} 가 외화자산과 같아지고 {@code fx_ratio} 가 항상 1.0 이었다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Asset", description = "보유 종목·외화 예금·원화 자산")
public class AssetController {

    private final HoldingService holdingService;
    private final DepositService depositService;
    private final KrwAssetService krwAssetService;

    public AssetController(
            HoldingService holdingService,
            DepositService depositService,
            KrwAssetService krwAssetService) {
        this.holdingService = holdingService;
        this.depositService = depositService;
        this.krwAssetService = krwAssetService;
    }

    // --- 보유 종목 ---

    @Operation(summary = "보유 종목 목록")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공. 없으면 빈 배열(FR-CM-09)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> listHoldings(@CurrentUser UUID userId) {
        List<HoldingResponse> data = holdingService.list(userId).stream()
                .map(AssetController::toHoldingResponse)
                .toList();
        return ApiResponse.of(data);
    }

    @Operation(summary = "종목 추가",
            description = "매입일을 넘기면 서버가 매입 시점 환율을 자동 조회한다(FR-ON-04).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력값 오류 또는 매입 환율 자동조회 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @PostMapping("/holdings")
    public ApiResponse<HoldingResponse> createHolding(
            @CurrentUser UUID userId,
            @Valid @RequestBody HoldingCreateRequest request) {
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

    @Operation(summary = "종목 수정",
            description = "수량·평균단가만 수정할 수 있다. 매입 환율 근거는 유지된다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "종목을 찾을 수 없음")})
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "종목을 찾을 수 없음")})
    @DeleteMapping("/holdings/{id}")
    public ApiResponse<Void> deleteHolding(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        holdingService.delete(userId, UUID.fromString(id));
        return ApiResponse.of(null);
    }

    // --- 외화 예금 ---

    @Operation(summary = "외화 예금 목록")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공. 없으면 빈 배열(FR-CM-09)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @GetMapping("/deposits")
    public ApiResponse<List<DepositResponse>> listDeposits(@CurrentUser UUID userId) {
        List<DepositResponse> data = depositService.list(userId).stream()
                .map(AssetController::toDepositResponse)
                .toList();
        return ApiResponse.of(data);
    }

    @Operation(summary = "외화 예금 추가",
            description = "매입일을 넘기면 서버가 예치 시점 환율을 자동 조회한다(FR-ON-04).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력값 오류 또는 예치 환율 자동조회 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @PostMapping("/deposits")
    public ApiResponse<DepositResponse> createDeposit(
            @CurrentUser UUID userId,
            @Valid @RequestBody DepositCreateRequest request) {
        Deposit created = depositService.create(
                userId,
                request.currencyCode(),
                request.amount(),
                request.purchasedAt(),
                request.purchaseFxRateKrw());
        return ApiResponse.of(toDepositResponse(created));
    }

    @Operation(summary = "외화 예금 수정",
            description = "잔액만 수정한다. 예치 시점 환율 근거는 유지된다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "잔액이 없거나 음수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "예금을 찾을 수 없음")})
    @PutMapping("/deposits/{id}")
    public ApiResponse<DepositResponse> updateDeposit(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody DepositUpdateRequest request) {
        Deposit updated = depositService.update(userId, UUID.fromString(id), request.amount());
        return ApiResponse.of(toDepositResponse(updated));
    }

    @Operation(summary = "외화 예금 삭제")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "예금을 찾을 수 없음")})
    @DeleteMapping("/deposits/{id}")
    public ApiResponse<Void> deleteDeposit(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        depositService.delete(userId, UUID.fromString(id));
        return ApiResponse.of(null);
    }

    // --- 원화 자산 (외화 비중의 분모) ---

    @Operation(summary = "원화 자산 목록",
            description = "외화 비중의 분모다. 총자산 = Σ 원화 자산 + Σ 외화 예금 + Σ 보유 종목.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공. 없으면 빈 배열(FR-CM-09)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @GetMapping("/krw-assets")
    public ApiResponse<List<KrwAssetResponse>> listKrwAssets(@CurrentUser UUID userId) {
        List<KrwAssetResponse> data = krwAssetService.list(userId).stream()
                .map(AssetController::toKrwAssetResponse)
                .toList();
        return ApiResponse.of(data);
    }

    @Operation(summary = "원화 자산 추가")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "종류가 허용값이 아니거나 금액이 음수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요")})
    @PostMapping("/krw-assets")
    public ApiResponse<KrwAssetResponse> createKrwAsset(
            @CurrentUser UUID userId,
            @RequestBody KrwAssetCreateRequest request) {
        KrwAsset created = krwAssetService.create(
                userId, request.kind(), request.label(), request.amountKrw());
        return ApiResponse.of(toKrwAssetResponse(created));
    }

    @Operation(summary = "원화 자산 수정")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "종류가 허용값이 아니거나 금액이 음수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "원화 자산을 찾을 수 없음")})
    @PutMapping("/krw-assets/{id}")
    public ApiResponse<KrwAssetResponse> updateKrwAsset(
            @CurrentUser UUID userId,
            @PathVariable String id,
            @RequestBody KrwAssetUpdateRequest request) {
        KrwAsset updated = krwAssetService.update(
                userId, UUID.fromString(id), request.kind(), request.label(), request.amountKrw());
        return ApiResponse.of(toKrwAssetResponse(updated));
    }

    @Operation(summary = "원화 자산 삭제")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "원화 자산을 찾을 수 없음")})
    @DeleteMapping("/krw-assets/{id}")
    public ApiResponse<Void> deleteKrwAsset(
            @CurrentUser UUID userId,
            @PathVariable String id) {
        krwAssetService.delete(userId, UUID.fromString(id));
        return ApiResponse.of(null);
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

    private static KrwAssetResponse toKrwAssetResponse(KrwAsset a) {
        return new KrwAssetResponse(
                a.getId().toString(), a.getKind(), a.getLabel(), a.getAmountKrw());
    }
}
