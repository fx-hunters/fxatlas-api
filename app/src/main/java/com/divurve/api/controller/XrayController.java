package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.xray.AttributionResponse;
import com.divurve.api.dto.xray.StressRequest;
import com.divurve.api.dto.xray.StressResponse;
import com.divurve.api.dto.xray.XrayResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.xray.XrayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * X-ray 진단 엔드포인트 (이슈 #14, 명세 3.3/3.4/3.5).
 * 사용자의 포트폴리오 분석: 통화 노출, 손익 분해, 스트레스 테스트.
 *
 * <p>대상 사용자는 {@link CurrentUser} 로 주입받는다. 이슈 #50 이전에는
 * {@code @RequestParam UUID userId} 로 받아, <b>아무나 남의 user_id 를 쿼리 파라미터에 넣어
 * 포트폴리오를 조회할 수 있었다</b>(NFR-SE-03 위반).
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/xray")
@Tag(name = "X-ray", description = "통화 노출·손익 분해·스트레스")
public class XrayController {

    private final XrayService xrayService;

    public XrayController(XrayService xrayService) {
        this.xrayService = Objects.requireNonNull(xrayService, "xrayService is null");
    }

    @Operation(summary = "통화 노출·외화 비중·민감도")
    @GetMapping
    public ApiResponse<XrayResponse> getXray(@CurrentUser UUID userId) {

        XrayService.PortfolioSnapshot snapshot = xrayService.getPortfolio(userId);

        // Exposure 리스트 구성
        List<XrayResponse.Exposure> exposures = new ArrayList<>();
        for (Map.Entry<String, Long> entry : snapshot.currencyToAssetKrw().entrySet()) {
            double share = snapshot.exposure().getOrDefault(entry.getKey(), 0.0);
            exposures.add(new XrayResponse.Exposure(entry.getKey(), entry.getValue(), share));
        }

        // Concentration 정보
        XrayResponse.Concentration concentration = new XrayResponse.Concentration(
                snapshot.concentrationTopCurrency(),
                snapshot.concentrationTopShare(),
                snapshot.concentrationThreshold(),
                snapshot.concentrationStatus()
        );

        // Sensitivity (1% 변동)
        Map<String, Long> sensitivityByCurrency = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : snapshot.currencyToAssetKrw().entrySet()) {
            long impact = Math.round(entry.getValue() * 0.01);
            sensitivityByCurrency.put(entry.getKey(), impact);
        }
        long totalSensitivity = sensitivityByCurrency.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        XrayResponse.Sensitivity sensitivity = new XrayResponse.Sensitivity(
                totalSensitivity,
                sensitivityByCurrency
        );

        // 예정 지출 (임시: 빈 리스트)
        List<XrayResponse.UpcomingOutflow> upcomingOutflows = new ArrayList<>();

        XrayResponse response = new XrayResponse(
                snapshot.totalAssetKrw(),
                snapshot.fxAssetKrw(),
                snapshot.fxRatio(),
                exposures,
                concentration,
                sensitivity,
                0L,
                upcomingOutflows
        );

        return ApiResponse.of(response);
    }

    @Operation(summary = "손익 분해")
    @GetMapping("/attribution")
    public ApiResponse<AttributionResponse> getAttribution(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) String mode) {

        XrayService.AttributionAnalysis analysis = xrayService.getAttribution(userId, currencyCode, mode);

        // 구성요소 리스트 구성
        List<AttributionResponse.Component> components = new ArrayList<>();
        components.add(new AttributionResponse.Component(
                "asset",
                analysis.asset().krwImpact(),
                analysis.asset().returnRatio() * 100
        ));
        components.add(new AttributionResponse.Component(
                "fx",
                analysis.fx().krwImpact(),
                analysis.fx().returnRatio() * 100
        ));
        components.add(new AttributionResponse.Component(
                "cost",
                analysis.cost().krwImpact(),
                analysis.cost().returnRatio() * 100
        ));

        if ("three_way".equals(analysis.mode())) {
            components.add(new AttributionResponse.Component(
                    "interaction",
                    analysis.interaction().krwImpact(),
                    analysis.interaction().returnRatio() * 100
            ));
        }

        // 종목별 분해 (임시: 빈 리스트)
        List<AttributionResponse.ByHolding> byHolding = new ArrayList<>();

        AttributionResponse response = new AttributionResponse(
                currencyCode != null ? currencyCode : "ALL",
                analysis.mode(),
                analysis.costBasisKrw(),
                analysis.currentKrw(),
                analysis.totalReturn(),
                components,
                byHolding
        );

        return ApiResponse.of(response);
    }

    @Operation(summary = "스트레스 시나리오 적용")
    @PostMapping("/stress")
    public ApiResponse<StressResponse> applyStress(
            @CurrentUser UUID userId,
            @RequestBody StressRequest request) {

        XrayService.StressAnalysis analysis = xrayService.applyStress(userId, request.shocks());

        // 통화별 영향도 리스트 구성
        List<StressResponse.ByCurrency> byCurrencyList = new ArrayList<>();
        for (XrayService.CurrencyStressImpactData impact : analysis.byCurrencyMap().values()) {
            byCurrencyList.add(new StressResponse.ByCurrency(
                    impact.currencyCode(),
                    impact.shock(),
                    impact.impactKrw()
            ));
        }

        StressResponse response = new StressResponse(
                analysis.totalAssetBeforeKrw(),
                analysis.totalAssetAfterKrw(),
                analysis.portfolioImpactKrw(),
                analysis.portfolioImpactRatio(),
                byCurrencyList
        );

        return ApiResponse.of(response);
    }

}
