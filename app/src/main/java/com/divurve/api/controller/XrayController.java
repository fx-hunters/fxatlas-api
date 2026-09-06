package com.divurve.api.controller;

import com.divurve.api.dto.xray.AttributionResponse;
import com.divurve.api.dto.xray.StressRequest;
import com.divurve.api.dto.xray.StressResponse;
import com.divurve.api.dto.xray.XrayResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.xray.XrayService;
import com.divurve.engine.attribution.AttributionCalculator;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.stress.StressCalculator;
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
    public ApiResponse<XrayResponse> getXray(@RequestParam UUID userId) {

        XrayService.PortfolioSnapshot snapshot = xrayService.getPortfolio(userId);

        // Exposure 리스트 구성
        List<XrayResponse.Exposure> exposures = new ArrayList<>();
        for (Map.Entry<String, Long> entry : snapshot.currencyToAssetKrw().entrySet()) {
            double share = snapshot.exposure().getOrDefault(entry.getKey(), 0.0);
            exposures.add(new XrayResponse.Exposure(entry.getKey(), entry.getValue(), share));
        }

        // Concentration 정보
        ConcentrationCalculator.ConcentrationResult conc = snapshot.concentration();
        XrayResponse.Concentration concentration = new XrayResponse.Concentration(
                conc.topCurrency(),
                conc.topShare(),
                conc.threshold(),
                conc.status()
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
            @RequestParam UUID userId,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) String mode) {

        XrayService.AttributionAnalysis analysis = xrayService.getAttribution(userId, currencyCode, mode);
        AttributionCalculator.AttributionResult result = analysis.result();

        // 구성요소 리스트 구성
        List<AttributionResponse.Component> components = new ArrayList<>();
        components.add(new AttributionResponse.Component(
                "asset",
                result.asset().krwImpact(),
                result.asset().returnRatio() * 100
        ));
        components.add(new AttributionResponse.Component(
                "fx",
                result.fx().krwImpact(),
                result.fx().returnRatio() * 100
        ));
        components.add(new AttributionResponse.Component(
                "cost",
                result.cost().krwImpact(),
                result.cost().returnRatio() * 100
        ));

        if ("three_way".equals(result.mode())) {
            components.add(new AttributionResponse.Component(
                    "interaction",
                    result.interaction().krwImpact(),
                    result.interaction().returnRatio() * 100
            ));
        }

        // 종목별 분해 (임시: 빈 리스트)
        List<AttributionResponse.ByHolding> byHolding = new ArrayList<>();

        AttributionResponse response = new AttributionResponse(
                currencyCode != null ? currencyCode : "ALL",
                result.mode(),
                result.costBasisKrw(),
                result.currentKrw(),
                result.totalReturn(),
                components,
                byHolding
        );

        return ApiResponse.of(response);
    }

    @Operation(summary = "스트레스 시나리오 적용")
    @PostMapping("/stress")
    public ApiResponse<StressResponse> applyStress(
            @RequestParam UUID userId,
            @RequestBody StressRequest request) {

        XrayService.StressAnalysis analysis = xrayService.applyStress(userId, request.shocks());
        StressCalculator.StressResult result = analysis.result();

        // 통화별 영향도 리스트 구성
        List<StressResponse.ByCurrency> byCurrencyList = new ArrayList<>();
        for (StressCalculator.CurrencyStressImpact impact : result.byCurrencyMap().values()) {
            byCurrencyList.add(new StressResponse.ByCurrency(
                    impact.currencyCode(),
                    impact.shock(),
                    impact.impactKrw()
            ));
        }

        StressResponse response = new StressResponse(
                result.totalAssetBeforeKrw(),
                result.totalAssetAfterKrw(),
                result.portfolioImpactKrw(),
                result.portfolioImpactRatio(),
                byCurrencyList
        );

        return ApiResponse.of(response);
    }

}
