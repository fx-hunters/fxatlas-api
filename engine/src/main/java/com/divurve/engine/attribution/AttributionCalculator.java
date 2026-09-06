package com.divurve.engine.attribution;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 손익 4분해 계산기 (FR-XR-04, 요구사항 v2 §4.6).
 *
 * <p>원화 수익률의 <b>산술</b> 검증식을 그대로 구현한다.
 * <pre>
 *   R_KRW = (1 + R_asset) × (1 + R_fx) − 1
 *         = R_asset + R_fx + R_asset × R_fx
 * </pre>
 * 여기에 거래비용 항을 더해 <b>asset · fx · interaction · cost</b> 네 항으로 분해한다.
 * 네 항의 원화 금액 합은 정의상 {@code current_krw − cost_basis_krw} 와 <b>정확히</b> 일치한다
 * (반올림 오차까지 없도록 {@code current_krw} 를 네 항의 합으로 되돌려 계산한다).
 *
 * <p><b>분해 방식은 고정이며 사용자 설정으로 바뀌지 않는다</b>(FR-CM-08 · FR-FC-08).
 * v1 의 {@code mode}(three_way/shapley) 분기는 API 명세 v2 §0.1 에서 삭제됐다.
 *
 * <p>이전 구현은 로그수익률 가법분해(`ln(V_end/V_start)`)를 썼다. 그 방식에서는
 * {@code interaction} 이 항상 0(≈ −6e−17)이고 각 항의 원화 환산이 {@code lnR × 원금} 이라
 * 네 항의 합이 총손익과 어긋났다(재현: 760,863 vs 780,425). 또 {@code safeLog} 가 전량 손실
 * ({@code A_end = 0}) 을 수익률 0 으로 만들었다. 산술식으로 바꾸면 전량 손실은 −100% 로 나온다.
 */
@EngineComponent
public class AttributionCalculator {

    /** 비율(수익률·기여도) 표기 자릿수. 명세 §1.4 "비율은 0~1 소수". */
    public static final int RATIO_SCALE = 4;

    /** 자산 가격 효과 키. */
    public static final String KEY_ASSET = "asset";
    /** 환율 효과 키. */
    public static final String KEY_FX = "fx";
    /** 상호작용(교차항) 키. */
    public static final String KEY_INTERACTION = "interaction";
    /** 거래비용 키. */
    public static final String KEY_COST = "cost";

    private static final MathContext DIVISION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final double EPSILON = 1e-10;

    /**
     * 손익을 자산·환율·상호작용·비용 네 항으로 분해한다.
     *
     * @param assetStartLocalCurrency 매입 시점 자산 평가액 (거래통화 기준, {@code > 0})
     * @param assetEndLocalCurrency   현재 자산 평가액 (거래통화 기준, {@code >= 0})
     * @param rateStartKrw            매입 시점 환율 (1 외화당 원화, {@code > 0})
     * @param rateEndKrw              현재 환율 (1 외화당 원화, {@code > 0})
     * @param costRatio               매입 원가 대비 거래비용 비율 (0~1)
     * @return 4분해 결과. {@code asset + fx + interaction + cost = current − cost_basis}
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    public AttributionResult decompose(
            double assetStartLocalCurrency,
            double assetEndLocalCurrency,
            BigDecimal rateStartKrw,
            BigDecimal rateEndKrw,
            double costRatio) {
        Objects.requireNonNull(rateStartKrw, "매입 시점 환율은 null일 수 없습니다.");
        Objects.requireNonNull(rateEndKrw, "현재 환율은 null일 수 없습니다.");
        validateInputs(assetStartLocalCurrency, assetEndLocalCurrency, rateStartKrw, rateEndKrw, costRatio);

        long costBasisKrw = BigDecimal.valueOf(assetStartLocalCurrency)
                .multiply(rateStartKrw)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        double assetReturn = assetEndLocalCurrency / assetStartLocalCurrency - 1.0;
        double fxReturn = rateEndKrw.divide(rateStartKrw, DIVISION_CONTEXT).doubleValue() - 1.0;
        double interactionReturn = assetReturn * fxReturn;
        double costReturn = -costRatio;

        AttributionComponent asset = component(KEY_ASSET, assetReturn, costBasisKrw);
        AttributionComponent fx = component(KEY_FX, fxReturn, costBasisKrw);
        AttributionComponent interaction = component(KEY_INTERACTION, interactionReturn, costBasisKrw);
        AttributionComponent cost = component(KEY_COST, costReturn, costBasisKrw);

        // 네 항의 합이 총손익과 어긋나지 않도록, 총손익을 합에서 되돌려 만든다(검산 항등식 보장).
        long totalGainKrw = asset.krw() + fx.krw() + interaction.krw() + cost.krw();
        long currentKrw = costBasisKrw + totalGainKrw;

        return new AttributionResult(
                costBasisKrw,
                currentKrw,
                contribution(totalGainKrw, costBasisKrw),
                ratio(assetReturn),
                ratio(fxReturn),
                asset,
                fx,
                interaction,
                cost);
    }

    /**
     * 종목별 4분해 결과를 하나로 합친다 ({@code GET /xray/attribution} 은 통화 단위로 응답한다).
     *
     * <p>각 항의 원화 금액을 더하고, 기여도는 <b>합산 매입원가 대비</b>로 다시 계산한다 — 이것이
     * 매입원가 가중평균 수익률과 같다. 항등식 {@code Σ항 = current − cost_basis} 는 종목별로 성립하므로
     * 합에서도 그대로 성립한다.
     *
     * @param results 종목별 분해 결과 (비어 있을 수 없음)
     * @return 합산 분해 결과
     * @throws IllegalArgumentException 입력이 비어 있는 경우
     */
    public AttributionResult aggregate(List<AttributionResult> results) {
        Objects.requireNonNull(results, "분해 결과 목록은 null일 수 없습니다.");
        if (results.isEmpty()) {
            throw new IllegalArgumentException("합산할 분해 결과가 없습니다.");
        }

        long costBasisKrw = 0L;
        long assetKrw = 0L;
        long fxKrw = 0L;
        long interactionKrw = 0L;
        long costKrw = 0L;

        for (AttributionResult result : results) {
            costBasisKrw += result.costBasisKrw();
            assetKrw += result.asset().krw();
            fxKrw += result.fx().krw();
            interactionKrw += result.interaction().krw();
            costKrw += result.cost().krw();
        }

        long totalGainKrw = assetKrw + fxKrw + interactionKrw + costKrw;
        long basis = costBasisKrw;

        return new AttributionResult(
                costBasisKrw,
                costBasisKrw + totalGainKrw,
                contribution(totalGainKrw, basis),
                contribution(assetKrw, basis),
                contribution(fxKrw, basis),
                new AttributionComponent(KEY_ASSET, assetKrw, contribution(assetKrw, basis)),
                new AttributionComponent(KEY_FX, fxKrw, contribution(fxKrw, basis)),
                new AttributionComponent(KEY_INTERACTION, interactionKrw, contribution(interactionKrw, basis)),
                new AttributionComponent(KEY_COST, costKrw, contribution(costKrw, basis)));
    }

    private double contribution(long krw, long costBasisKrw) {
        return costBasisKrw == 0L ? 0.0 : ratio((double) krw / costBasisKrw);
    }

    /**
     * 자산 수익률과 환율 수익률로 원화 수익률을 합성한다 (요구사항 v2 §4.6 검증식).
     * 종목별 표시({@code by_holding.krw_return})가 쓰는 순수 함수다.
     *
     * @param assetReturn 거래통화 기준 자산 수익률
     * @param fxReturn    환율 수익률
     * @return 원화 기준 수익률 {@code (1 + R_asset)(1 + R_fx) − 1}
     */
    public double krwReturn(double assetReturn, double fxReturn) {
        return ratio((1.0 + assetReturn) * (1.0 + fxReturn) - 1.0);
    }

    private AttributionComponent component(String key, double returnRatio, long costBasisKrw) {
        long krw = BigDecimal.valueOf(returnRatio)
                .multiply(BigDecimal.valueOf(costBasisKrw))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return new AttributionComponent(key, krw, contribution(krw, costBasisKrw));
    }

    private double ratio(double value) {
        return BigDecimal.valueOf(value).setScale(RATIO_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private void validateInputs(
            double assetStart,
            double assetEnd,
            BigDecimal rateStart,
            BigDecimal rateEnd,
            double costRatio) {
        if (assetStart <= EPSILON) {
            throw new IllegalArgumentException("매입 시점 자산 평가액은 0보다 커야 합니다 (입력 " + assetStart + ").");
        }
        if (assetEnd < 0) {
            throw new IllegalArgumentException("현재 자산 평가액은 음수일 수 없습니다 (입력 " + assetEnd + ").");
        }
        if (rateStart.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("매입 시점 환율은 0보다 커야 합니다 (입력 " + rateStart + ").");
        }
        if (rateEnd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("현재 환율은 0보다 커야 합니다 (입력 " + rateEnd + ").");
        }
        if (costRatio < 0 || costRatio > 1) {
            throw new IllegalArgumentException("비용 비율은 0~1 범위여야 합니다 (입력 " + costRatio + ").");
        }
    }

    /**
     * 4분해 결과.
     *
     * @param costBasisKrw 매입 원가 (원화 환산)
     * @param currentKrw   현재 평가액 (원화, 비용 반영 후). {@code costBasisKrw + 네 항의 합}
     * @param totalReturn  총 원화 수익률
     * @param assetReturn  거래통화 기준 자산 수익률 (종목 표시용)
     * @param fxReturn     환율 수익률 (종목 표시용)
     */
    public record AttributionResult(
            long costBasisKrw,
            long currentKrw,
            double totalReturn,
            double assetReturn,
            double fxReturn,
            AttributionComponent asset,
            AttributionComponent fx,
            AttributionComponent interaction,
            AttributionComponent cost
    ) {
    }

    /**
     * 4분해 구성요소.
     *
     * @param key            {@code asset} / {@code fx} / {@code interaction} / {@code cost}
     * @param krw            원화 손익 기여액
     * @param contributionPp 매입 원가 대비 기여 비율 (0~1 소수, 명세 §1.4)
     */
    public record AttributionComponent(
            String key,
            long krw,
            double contributionPp
    ) {
    }
}
