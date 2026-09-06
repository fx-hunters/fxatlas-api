package com.divurve.api.dto.xray;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 손익 4분해 응답 (API 명세 v2 §5.4 {@code GET /xray/attribution}).
 *
 * <p>요구사항 v2 §4.6 검증식 {@code R_KRW = (1+R_asset)(1+R_fx) − 1} 에 거래비용을 더한
 * <b>asset · fx · interaction · cost</b> 네 항 고정 분해다. {@code components[].krw} 의 합은
 * {@code current_krw − cost_basis_krw} 와 정확히 같다.
 *
 * <p>v1 의 {@code mode}(three_way/shapley)는 명세 §0.1 에서 삭제됐다 — 분해 방식은 사용자 설정으로
 * 바뀌지 않는다. {@code contribution_pp} 도 퍼센트(×100)가 아니라 §1.4 규약대로 0~1 비율이다.
 */
@Schema(description = "손익 4분해 (자산·환율·상호작용·비용)")
public record AttributionResponse(
        @Schema(description = "조회한 통화. 전체 조회면 null", example = "USD", nullable = true)
        String currencyCode,

        @Schema(description = "매입 원가 (원화 환산)", example = "15050000")
        long costBasisKrw,

        @Schema(description = "현재 평가액 (원화)", example = "15790000")
        long currentKrw,

        @Schema(description = "총 원화 수익률 (0~1 비율)", example = "0.0492")
        double totalReturn,

        @Schema(description = "asset · fx · interaction · cost 네 항. 순서와 개수는 고정이다")
        List<Component> components,

        @Schema(description = "종목별 분해")
        List<ByHolding> byHolding) {

    /** 손익 구성요소. */
    @Schema(description = "손익 구성요소")
    public record Component(
            @Schema(description = "구성요소 키", example = "asset",
                    allowableValues = {"asset", "fx", "interaction", "cost"})
            String key,

            @Schema(description = "화면 표시용 한글 이름", example = "자산 가격 효과")
            String label,

            @Schema(description = "원화 손익 기여액", example = "1241307")
            long krw,

            @Schema(description = "매입 원가 대비 기여 비율 (0~1)", example = "0.0825")
            double contributionPp) {
    }

    /** 종목별 손익 분해. */
    @Schema(description = "종목별 손익")
    public record ByHolding(
            @Schema(description = "종목 코드", example = "VOO") String ticker,
            @Schema(description = "현재 원화 평가액", example = "11240000") long krw,
            @Schema(description = "거래통화 기준 수익률", example = "0.091") double localReturn,
            @Schema(description = "환율 수익률", example = "-0.03") double fxReturn,
            @Schema(description = "원화 기준 수익률 = (1+local)(1+fx)−1", example = "0.0583")
            double krwReturn) {
    }
}
