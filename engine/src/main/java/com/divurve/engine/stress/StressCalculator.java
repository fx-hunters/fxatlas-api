package com.divurve.engine.stress;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 스트레스 테스트 계산기 (이슈 #14, FR-XR-07/08).
 * 통화별 환율 충격(shock) 시나리오를 받아 포트폴리오의 평가액 변화를 계산한다.
 * 로직: 새 환율 = 기존 환율 × (1 + shock), 새 평가액 = 외국 자산 × 새 환율
 */
@EngineComponent
public class StressCalculator {

    private static final int PRECISION_SCALE = 4;
    private static final double EPSILON = 1e-10;

    /**
     * 스트레스 시나리오를 적용한 포트폴리오 평가액 변화를 계산한다.
     *
     * @param currencyToAssetLocalCurrency 통화코드 → 자산(로컬 통화)의 맵
     * @param currencyToRateKrw           통화코드 → 환율(원화/외통)의 맵
     * @param currencyToShock             통화코드 → 환율 충격률(음수 가능)의 맵
     * @return 스트레스 계산 결과
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    public StressResult apply(
            Map<String, Double> currencyToAssetLocalCurrency,
            Map<String, BigDecimal> currencyToRateKrw,
            Map<String, Double> currencyToShock) {
        Objects.requireNonNull(currencyToAssetLocalCurrency, "자산 맵은 null일 수 없습니다.");
        Objects.requireNonNull(currencyToRateKrw, "환율 맵은 null일 수 없습니다.");
        Objects.requireNonNull(currencyToShock, "충격 맵은 null일 수 없습니다.");

        long totalBeforeKrw = 0L;
        long totalAfterKrw = 0L;
        Map<String, CurrencyStressImpact> impacts = new LinkedHashMap<>();

        for (String currency : currencyToAssetLocalCurrency.keySet()) {
            double assetLocal = currencyToAssetLocalCurrency.get(currency);
            BigDecimal rateKrw = currencyToRateKrw.get(currency);
            double shock = currencyToShock.getOrDefault(currency, 0.0);

            if (assetLocal < 0) {
                throw new IllegalArgumentException(
                        "통화 " + currency + "의 자산은 음수일 수 없습니다 (입력 " + assetLocal + ").");
            }
            if (rateKrw == null || rateKrw.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "통화 " + currency + "의 환율은 null이거나 0 이하일 수 없습니다 (입력 " + rateKrw + ").");
            }

            // 충격 전 평가액
            BigDecimal valueBeforeKrw = BigDecimal.valueOf(assetLocal)
                    .multiply(rateKrw)
                    .setScale(0, RoundingMode.HALF_UP);

            // 충격 후 환율 = 기존 환율 × (1 + shock)
            BigDecimal shockFactor = BigDecimal.ONE.add(BigDecimal.valueOf(shock));
            BigDecimal rateAfterKrw = rateKrw.multiply(shockFactor)
                    .setScale(PRECISION_SCALE, RoundingMode.HALF_UP);

            // 충격 후 평가액
            BigDecimal valueAfterKrw = BigDecimal.valueOf(assetLocal)
                    .multiply(rateAfterKrw)
                    .setScale(0, RoundingMode.HALF_UP);

            long impactKrw = valueAfterKrw.subtract(valueBeforeKrw).longValue();

            totalBeforeKrw += valueBeforeKrw.longValue();
            totalAfterKrw += valueAfterKrw.longValue();

            impacts.put(
                    currency,
                    new CurrencyStressImpact(currency, shock, impactKrw)
            );
        }

        long portfolioImpactKrw = totalAfterKrw - totalBeforeKrw;
        double portfolioImpactRatio = totalBeforeKrw > 0L
                ? (double) portfolioImpactKrw / totalBeforeKrw
                : 0.0;

        return new StressResult(
                totalBeforeKrw,
                totalAfterKrw,
                portfolioImpactKrw,
                portfolioImpactRatio,
                impacts
        );
    }

    /** 스트레스 테스트 결과 DTO. */
    public record StressResult(
            long totalAssetBeforeKrw,
            long totalAssetAfterKrw,
            long portfolioImpactKrw,
            double portfolioImpactRatio,
            Map<String, CurrencyStressImpact> byCurrencyMap
    ) {
    }

    /** 통화별 스트레스 영향 DTO. */
    public record CurrencyStressImpact(
            String currencyCode,
            double shock,
            long impactKrw
    ) {
    }
}
