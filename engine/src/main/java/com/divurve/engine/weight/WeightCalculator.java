package com.divurve.engine.weight;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 자산 비중 계산기 (이슈 #14, FR-XR-01/02).
 * 총자산 대비 외화 비중, 외화 내 통화별 비중·금액을 결정론적으로 계산한다.
 * 로직: 자산 = 외화 + 원화. 외화 비중 = 외화 / 자산. 통화별 비중 = 통화금액 / 외화.
 */
@EngineComponent
public class WeightCalculator {

    private static final int PRECISION_SCALE = 4;
    private static final int PERCENT_SCALE = 2;

    /**
     * 총자산 대비 외화 비중을 계산한다.
     *
     * @param totalAssetKrw 총자산 (원화 기준, 음수 불가)
     * @param fxAssetKrw    외화자산 (원화 환산, 음수 불가)
     * @return 외화 비중 (0.0 ~ 1.0)
     * @throws IllegalArgumentException 입력값이 음수인 경우
     */
    public double calculateFxRatio(long totalAssetKrw, long fxAssetKrw) {
        validateNonNegative(totalAssetKrw, "총자산");
        validateNonNegative(fxAssetKrw, "외화자산");

        if (totalAssetKrw == 0L) {
            return 0.0;
        }

        return BigDecimal.valueOf(fxAssetKrw)
                .divide(BigDecimal.valueOf(totalAssetKrw), PRECISION_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 통화별 비중을 계산한다.
     *
     * @param currencyAssetKrw 통화별 금액 (원화 환산, 음수 불가)
     * @param fxAssetKrw       총 외화자산 (원화 환산, 음수 불가)
     * @return 통화 비중 (0.0 ~ 1.0)
     * @throws IllegalArgumentException 입력값이 음수인 경우
     */
    public double calculateCurrencyShare(long currencyAssetKrw, long fxAssetKrw) {
        validateNonNegative(currencyAssetKrw, "통화자산");
        validateNonNegative(fxAssetKrw, "외화자산");

        if (fxAssetKrw == 0L) {
            return 0.0;
        }

        return BigDecimal.valueOf(currencyAssetKrw)
                .divide(BigDecimal.valueOf(fxAssetKrw), PRECISION_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 통화별 노출을 종합한 맵을 반환한다. LinkedHashMap을 사용하여 삽입 순서를 유지한다.
     *
     * @param currencyToAssetKrw 통화코드 → 금액(원화)의 맵
     * @param fxAssetKrw 총 외화자산 (원화)
     * @return 통화코드 → 비중의 LinkedHashMap (삽입 순서 유지)
     * @throws IllegalArgumentException 통화 금액이 음수인 경우
     */
    public Map<String, Double> calculateExposureMap(
            Map<String, Long> currencyToAssetKrw, long fxAssetKrw) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : currencyToAssetKrw.entrySet()) {
            String currency = entry.getKey();
            long assetKrw = entry.getValue();

            double share = calculateCurrencyShare(assetKrw, fxAssetKrw);
            result.put(currency, share);
        }

        return result;
    }

    private void validateNonNegative(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + "은 음수일 수 없습니다 (입력 " + value + ").");
        }
    }
}
