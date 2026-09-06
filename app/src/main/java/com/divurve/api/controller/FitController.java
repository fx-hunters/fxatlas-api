package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.fit.FitPreviewRequest;
import com.divurve.api.dto.fit.FitPreviewResponse;
import com.divurve.api.dto.fit.FitResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.fit.FitService;
import com.divurve.domain.settings.RiskProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fit 엔드포인트 (API 명세 v2 §5.5 · §5.6).
 *
 * <p>경로가 v2 에서 바뀌었다: {@code GET /fit/concentration} → {@code GET /fit},
 * {@code POST /fit/simulate} → {@code POST /fit/preview}.
 *
 * <p>v1 의 {@code suggestions}("USD 비중이 높습니다. 분산 투자를 고려하세요.")와
 * {@code suggested_goal} 은 <b>삭제</b>했다 — 서버가 통화별 매수를 제안하는 것은
 * FR-FT-04·FR-FT-06 에 정면으로 어긋난다. 관계는 코드와 사실값으로만 내려보내고,
 * 문장화는 {@code POST /ai/explain} 이 맡는다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/fit")
@Tag(name = "Fit", description = "성향과 현재 노출의 관계·집중도·참고 기준선")
public class FitController {

    private final FitService fitService;

    public FitController(FitService fitService) {
        this.fitService = Objects.requireNonNull(fitService, "fitService is null");
    }

    @Operation(summary = "성향과 현재 노출의 관계",
            description = "`relation` 은 코드와 사실값만 담는다. 적합·부적합 판정이나 점수·등급은 내리지 "
                    + "않는다(FR-FT-04). 성향 미측정이면 기준선이 null 이고 관계 코드는 "
                    + "`risk_profile_not_measured` 다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "사용자를 찾을 수 없음")})
    @GetMapping
    public ApiResponse<FitResponse> getFit(@CurrentUser UUID userId) {
        FitService.FitDiagnosis diagnosis = fitService.getFit(userId);
        RiskProfileView profile = diagnosis.riskProfile();
        FitService.ConcentrationView concentration = diagnosis.concentration();

        return ApiResponse.of(new FitResponse(
                new FitResponse.RiskProfile(
                        profile.status(),
                        profile.riskType(),
                        profile.gradeLabel(),
                        profile.diagnosedOn()),
                new FitResponse.Concentration(
                        concentration.topCurrencyCode(),
                        concentration.share(),
                        concentration.threshold(),
                        concentration.status()),
                new FitResponse.Relation(
                        diagnosis.relationCode(),
                        new FitResponse.Facts(
                                concentration.share(),
                                concentration.threshold(),
                                concentration.gapPp())),
                FitService.BASIS_NOTE));
    }

    @Operation(summary = "통화 비중 가정 미리보기",
            description = "외화자산 총액을 고정한 채 한 통화의 비중만 바꿨을 때의 집중도·민감도 변화만 "
                    + "반환한다(FR-FT-03). 저장하지 않으며, 목표를 제안하지 않는다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "계산 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "통화가 비었거나 포트폴리오에 없거나 조정 후 비중이 0~1 범위를 벗어남"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "사용자를 찾을 수 없음")})
    @PostMapping("/preview")
    public ApiResponse<FitPreviewResponse> preview(
            @CurrentUser UUID userId,
            @RequestBody FitPreviewRequest request) {

        FitService.FitPreview preview =
                fitService.preview(userId, request.currencyCode(), request.deltaShare());

        return ApiResponse.of(new FitPreviewResponse(
                assumption(preview),
                new FitPreviewResponse.Exposure(preview.exposureBefore(), preview.exposureAfter()),
                new FitPreviewResponse.Concentration(
                        snapshot(preview.concentrationBefore()),
                        snapshot(preview.concentrationAfter()),
                        preview.threshold()),
                new FitPreviewResponse.Sensitivity(
                        sensitivity(preview.sensitivityBefore()),
                        sensitivity(preview.sensitivityAfter()))));
    }

    /** 명세 §5.6 의 {@code assumption} 문구. 사실 진술이며 권유가 아니다. */
    private static String assumption(FitService.FitPreview preview) {
        long deltaPp = Math.round(preview.deltaShare() * 100);
        return "외화자산 총액 %s원을 고정한 채 %s 비중만 %d%%p %s 가정입니다.".formatted(
                NumberFormat.getNumberInstance(Locale.KOREA).format(preview.fxAssetKrw()),
                preview.currencyCode(),
                Math.abs(deltaPp),
                deltaPp < 0 ? "낮춘" : "높인");
    }

    private static FitPreviewResponse.Snapshot snapshot(FitService.ConcentrationView view) {
        return new FitPreviewResponse.Snapshot(
                view.topCurrencyCode(), view.share(), view.status());
    }

    /** 명세 §5.6 예시대로 통화별 민감도와 {@code total_krw} 를 한 객체에 담는다. */
    private static Map<String, Long> sensitivity(FitService.SensitivityView sensitivity) {
        Map<String, Long> flattened = new LinkedHashMap<>(sensitivity.byCurrency());
        flattened.put(FitPreviewResponse.Sensitivity.TOTAL_KEY, sensitivity.totalKrw());
        return flattened;
    }
}
