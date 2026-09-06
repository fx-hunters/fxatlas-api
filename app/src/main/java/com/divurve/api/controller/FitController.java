package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.fit.ConcentrationResponse;
import com.divurve.api.dto.fit.SimulateRequest;
import com.divurve.api.dto.fit.SimulateResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.fit.FitService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Fit(적합성) 엔드포인트 (이슈 #14, 명세 3.8).
 * 포트폴리오 집중도 진단과 분산효과 시뮬레이션.
 *
 * <p>대상 사용자는 {@link CurrentUser} 로 주입받는다. 이슈 #50 이전에는
 * {@code @RequestParam UUID userId} 로 받아, <b>아무나 남의 user_id 를 쿼리 파라미터에 넣어
 * 집중도 진단을 조회할 수 있었다</b>(NFR-SE-03 위반).
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/fit")
@Tag(name = "Fit", description = "집중도 진단·분산효과 시뮬레이션")
public class FitController {

    private final FitService fitService;

    public FitController(FitService fitService) {
        this.fitService = Objects.requireNonNull(fitService, "fitService is null");
    }

    @Operation(summary = "집중도 진단")
    @GetMapping("/concentration")
    public ApiResponse<ConcentrationResponse> getConcentration(@CurrentUser UUID userId) {

        FitService.ConcentrationDiagnosis diagnosis = fitService.diagnoseConcentration(userId);

        ConcentrationResponse response = new ConcentrationResponse(
                diagnosis.exposure(),
                diagnosis.topCurrency(),
                diagnosis.topShare(),
                diagnosis.threshold(),
                diagnosis.status(),
                buildSuggestions(diagnosis.status(), diagnosis.topCurrency())
        );

        return ApiResponse.of(response);
    }

    @Operation(summary = "분산효과 시뮬레이션")
    @PostMapping("/simulate")
    public ApiResponse<SimulateResponse> simulate(
            @CurrentUser UUID userId,
            @RequestBody SimulateRequest request) {

        FitService.DiversificationSimulation simulation = fitService.simulateDiversification(
                userId,
                request.currencyCode(),
                request.deltaShare()
        );

        // Portfolio volatility 구성
        SimulateResponse.PortfolioVol portfolioVol = new SimulateResponse.PortfolioVol(
                simulation.portfolioVolBefore(),
                simulation.portfolioVolAfter()
        );

        // 조정 후 노출도
        Map<String, Double> exposureAfter = new LinkedHashMap<>(simulation.adjustedShare());

        // 임계값 이내 여부
        boolean withinThreshold = simulation.topShareAfter() <= simulation.thresholdAfter();

        // Suggested goal (FR-FT-04)
        SimulateResponse.SuggestedGoal suggestedGoal = new SimulateResponse.SuggestedGoal(
                "saving", // kind
                "diversification", // purpose
                request.currencyCode(),
                0.0 // targetAmount (UI에서 채움)
        );

        SimulateResponse response = new SimulateResponse(
                portfolioVol,
                exposureAfter,
                simulation.thresholdAfter(),
                withinThreshold,
                suggestedGoal
        );

        return ApiResponse.of(response);
    }

    private List<String> buildSuggestions(String status, String topCurrency) {
        List<String> suggestions = new ArrayList<>();

        if ("warning".equals(status)) {
            suggestions.add(topCurrency + " 비중이 높습니다. 분산 투자를 고려하세요.");
            suggestions.add("다른 통화에 적립하여 위험을 분산하세요.");
        } else {
            suggestions.add("포트폴리오 균형이 적절합니다.");
        }

        return suggestions;
    }

}
