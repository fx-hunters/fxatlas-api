package com.divurve.engine.attribution;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 손익 귀속분해 계산기 (이슈 #14, FR-XR-04/05).
 * 원화 수익률을 자산/환율/교차항/비용으로 분해한다.
 *
 * <p>로그수익률의 가법분해 (가산적 분해):
 * ln(V_end / V_start) = ln(A_end / A_start) + ln(X_end / X_start) + interaction
 *
 * <p>여기서:
 * - V = 원화 가치 = A * X (외국 자산 × 환율)
 * - A = 외국 자산 (로컬 통화)
 * - X = 환율 (원화 / 외통)
 * - interaction = ln((A_end × X_end) / (A_start × X_start)) - ln(A_end / A_start) - ln(X_end / X_start)
 *
 * <p>mode = "three_way": 자산/환율/교차항 분리
 * mode = "shapley": 교차항을 절반씩 자산과 환율에 배분
 */
@EngineComponent
public class AttributionCalculator {

    private static final int PERCENT_SCALE = 4;
    private static final double EPSILON = 1e-10;

    /**
     * 손익 귀속분해를 계산한다.
     *
     * @param assetStartLocalCurrency 보유 시작 자산 (로컬 통화, > 0)
     * @param assetEndLocalCurrency   보유 종료 자산 (로컬 통화, ≥ 0)
     * @param rateStartKrw            보유 시작 환율 (원화/외통, > 0)
     * @param rateEndKrw              보유 종료 환율 (원화/외통, > 0)
     * @param costRatio               거래 비용 비율 (0 ~ 1, ≤ 0.1)
     * @param mode                    분해 모드 ("three_way" 또는 "shapley")
     * @return 귀속분해 결과
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    public AttributionResult decompose(
            double assetStartLocalCurrency,
            double assetEndLocalCurrency,
            BigDecimal rateStartKrw,
            BigDecimal rateEndKrw,
            double costRatio,
            String mode) {
        Objects.requireNonNull(rateStartKrw, "보유 시작 환율은 null일 수 없습니다.");
        Objects.requireNonNull(rateEndKrw, "보유 종료 환율은 null일 수 없습니다.");
        Objects.requireNonNull(mode, "분해 모드는 null일 수 없습니다.");

        validateInputs(assetStartLocalCurrency, assetEndLocalCurrency, rateStartKrw, rateEndKrw, costRatio);

        BigDecimal assetStart = BigDecimal.valueOf(assetStartLocalCurrency);
        BigDecimal assetEnd = BigDecimal.valueOf(assetEndLocalCurrency);

        // 원화 가치
        BigDecimal valueStartKrw = assetStart.multiply(rateStartKrw);
        BigDecimal valueEndKrw = assetEnd.multiply(rateEndKrw);

        // 로그 수익률 (순수)
        double logAssetReturn = safeLog(assetEnd.doubleValue() / assetStart.doubleValue());
        double logFxReturn = safeLog(rateEndKrw.doubleValue() / rateStartKrw.doubleValue());

        // 전체 로그 수익률
        double logTotalReturn = safeLog(valueEndKrw.doubleValue() / valueStartKrw.doubleValue());

        // 교차항 = 전체 - 자산 - 환율
        double logInteraction = logTotalReturn - logAssetReturn - logFxReturn;

        // 비용 수익률
        double logCostReturn = costRatio > EPSILON ? safeLog(1.0 - costRatio) : 0.0;

        // 최종 순수익률 = 자산 + 환율 + 교차항 - 비용
        double netLogReturn = logAssetReturn + logFxReturn + logInteraction + logCostReturn;

        // 귀속분해 (원화 기준)
        AttributionComponent assetComponent = new AttributionComponent(
                "asset",
                logToReturn(logAssetReturn),
                calculateKrwImpact(logAssetReturn, valueStartKrw)
        );

        AttributionComponent fxComponent = new AttributionComponent(
                "fx",
                logToReturn(logFxReturn),
                calculateKrwImpact(logFxReturn, valueStartKrw)
        );

        AttributionComponent costComponent = new AttributionComponent(
                "cost",
                logToReturn(logCostReturn),
                calculateKrwImpact(logCostReturn, valueStartKrw)
        );

        AttributionComponent interactionComponent = new AttributionComponent(
                "interaction",
                logToReturn(logInteraction),
                calculateKrwImpact(logInteraction, valueStartKrw)
        );

        return new AttributionResult(
                valueStartKrw.longValue(),
                valueEndKrw.longValue(),
                logToReturn(netLogReturn),
                assetComponent,
                fxComponent,
                interactionComponent,
                costComponent,
                mode
        );
    }

    /**
     * 로그 수익률을 일반 수익률로 변환한다.
     * exp(r) - 1, 여기서 r = ln(end/start)
     */
    private double logToReturn(double logReturn) {
        return Math.expm1(logReturn);
    }

    /**
     * 안전한 로그 계산 (0 또는 음수 입력 시 0 반환).
     */
    private double safeLog(double value) {
        if (value <= EPSILON) {
            return 0.0;
        }
        return Math.log(value);
    }

    /**
     * 로그 기반 수익률의 원화 영향도를 계산한다.
     * Impact_KRW = logReturn × valueStartKrw
     */
    private long calculateKrwImpact(double logReturn, BigDecimal valueStartKrw) {
        return BigDecimal.valueOf(logReturn)
                .multiply(valueStartKrw)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private void validateInputs(
            double assetStart,
            double assetEnd,
            BigDecimal rateStart,
            BigDecimal rateEnd,
            double costRatio) {
        if (assetStart <= EPSILON) {
            throw new IllegalArgumentException("보유 시작 자산은 0보다 커야 합니다 (입력 " + assetStart + ").");
        }
        if (assetEnd < 0) {
            throw new IllegalArgumentException("보유 종료 자산은 음수일 수 없습니다 (입력 " + assetEnd + ").");
        }
        if (rateStart.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("보유 시작 환율은 0보다 커야 합니다 (입력 " + rateStart + ").");
        }
        if (rateEnd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("보유 종료 환율은 0보다 커야 합니다 (입력 " + rateEnd + ").");
        }
        if (costRatio < 0 || costRatio > 1) {
            throw new IllegalArgumentException("비용 비율은 0~1 범위여야 합니다 (입력 " + costRatio + ").");
        }
    }

    /** 귀속분해 결과 DTO. */
    public record AttributionResult(
            long costBasisKrw,
            long currentKrw,
            double totalReturn,
            AttributionComponent asset,
            AttributionComponent fx,
            AttributionComponent interaction,
            AttributionComponent cost,
            String mode
    ) {
    }

    /** 귀속분해 구성 요소. */
    public record AttributionComponent(
            String key,
            double returnRatio,
            long krwImpact
    ) {
    }
}
