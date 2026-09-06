package com.divurve.api.dto.market;

import com.divurve.domain.market.MarketRegimeService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시장 상태 응답 ({@code GET /market/regime}, 명세 v2 §5.10).
 *
 * <p>v1 의 {@code GET /system/safe-mode} 를 대체한다. <b>상태를 알리되 기능을 끄지 않는다</b> —
 * v1 어휘였던 "안전모드"와 {@code 503 SAFE_MODE_ACTIVE} 는 명세 v2 §0.1 에서 삭제됐다.
 */
@Schema(description = "시장 상태 배지와 판정 근거. 어떤 상태에서도 응답을 막지 않는다.")
public record MarketRegimeResponse(
        @Schema(description = "화면 상단 배지",
                allowableValues = {"normal", "caution", "turbulent"}, example = "caution")
        String badge,

        @Schema(description = "배지 표시 문구", allowableValues = {"정상", "주의", "급변"}, example = "주의")
        String badgeLabel,

        @Schema(description = "대표 국면 (가장 심각한 통화쌍 기준)",
                allowableValues = {"calm", "normal", "elevated", "stress"}, example = "elevated")
        String regime,

        @Schema(description = "통화쌍별 국면. 데이터가 없는 통화쌍은 포함되지 않는다.")
        Map<String, PairRegime> pairRegimes,

        @Schema(description = "판정 근거. 실패해도 어떤 기능도 끄지 않는다(FR-SF-01).")
        List<Check> checks,

        @Schema(description = "클라이언트 표시 안내")
        Guidance guidance,

        @Schema(description = "데이터 오류 여부. 실제 시장 충격과 구분한다(FR-SF-06).")
        Anomaly anomaly) {

    /** 도메인 뷰를 응답 DTO 로 옮긴다. */
    public static MarketRegimeResponse from(MarketRegimeService.MarketRegimeView view) {
        Map<String, PairRegime> pairs = new LinkedHashMap<>();
        view.pairRegimes().forEach((pairCode, pair) ->
                pairs.put(pairCode, new PairRegime(pair.regime(), pair.vol30d(), pair.volPercentile5y())));

        return new MarketRegimeResponse(
                view.badge(),
                view.badgeLabel(),
                view.regime(),
                pairs,
                view.checks().stream()
                        .map(check -> new Check(check.key(), check.passed(), check.detail()))
                        .toList(),
                new Guidance(
                        view.guidance().keepServingForecast(),
                        view.guidance().widenUncertainty(),
                        view.guidance().showPlanAssumptions()),
                new Anomaly(view.anomaly().dataErrorDetected(), view.anomaly().note()));
    }

    /**
     * 통화쌍 하나의 국면.
     *
     * @param regime          국면 코드
     * @param vol30d          30일 실현변동성 (연환산)
     * @param volPercentile5y 5년 변동성 백분위 (0~1 비율)
     */
    @Schema(description = "통화쌍 하나의 국면")
    public record PairRegime(
            @Schema(allowableValues = {"calm", "normal", "elevated", "stress"}, example = "elevated")
            String regime,

            @Schema(description = "30일 실현변동성 (연환산)", example = "0.061")
            double vol30d,

            @Schema(description = "5년 변동성 백분위 (0~1 비율)", example = "0.72")
            double volPercentile5y) {
    }

    /**
     * 판정 근거 한 항목.
     *
     * @param key    항목 키
     * @param passed 통과 여부
     * @param detail 실패 사유 (통과 시 생략)
     */
    @Schema(description = "판정 근거 한 항목")
    public record Check(
            @Schema(allowableValues = {"data_freshness", "source_divergence", "vol_percentile"},
                    example = "vol_percentile")
            String key,

            @Schema(example = "false") boolean passed,

            @Schema(description = "실패 사유. 통과 시 생략된다.",
                    example = "USDKRW 30일 변동성이 5년 상위 28% 구간입니다.")
            String detail) {
    }

    /**
     * 클라이언트 표시 안내.
     *
     * @param keepServingForecast 항상 {@code true} — 산출을 멈추는 경로가 없다 (FR-SF-01)
     * @param widenUncertainty    불확실성 안내 강화 (FR-SF-03)
     * @param showPlanAssumptions 기존 계획의 기준일·가정 확인 (FR-SF-04)
     */
    @Schema(description = "클라이언트 표시 안내")
    public record Guidance(
            @Schema(description = "항상 true. 어떤 상태에서도 전망 산출을 멈추지 않는다(FR-SF-01).",
                    example = "true")
            boolean keepServingForecast,

            @Schema(example = "true") boolean widenUncertainty,

            @Schema(example = "true") boolean showPlanAssumptions) {
    }

    /**
     * 이상 징후.
     *
     * @param dataErrorDetected 데이터 품질 문제 여부 (변동성 확대는 여기 해당하지 않는다)
     * @param note              구분 원칙 안내 문구
     */
    @Schema(description = "이상 징후. 데이터 오류와 실제 시장 충격을 구분한다.")
    public record Anomaly(
            @Schema(example = "false") boolean dataErrorDetected,
            String note) {
    }
}
