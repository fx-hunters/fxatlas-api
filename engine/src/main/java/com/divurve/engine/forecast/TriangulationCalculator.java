package com.divurve.engine.forecast;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 삼각 무차익 조건으로 환율 유도 (FR-FC-09).
 *
 * <p>모델링 대상: USD/KRW, USD/JPY, EUR/USD (3개만 직접 모델링).
 * 나머지는 삼각 무차익 조건(arbitrage-free triangulation)으로 유도:
 * - EUR/KRW = EUR/USD × USD/KRW
 * - EUR/JPY = EUR/USD × USD/JPY
 * - GBP/KRW = GBP/USD × USD/KRW  (GBP/USD 필요)
 *
 * <p>모든 입력은 기준 시각을 갖고 있어야 한다 (NFR-DT-01).
 */
public class TriangulationCalculator {

    private static final String USD_KRW = "USD_KRW";
    private static final String USD_JPY = "USD_JPY";
    private static final String EUR_USD = "EUR_USD";

    /**
     * 삼각 무차익으로 추가 통화쌍 환율을 유도한다.
     *
     * <p>입력: USD/KRW, USD/JPY, EUR/USD (최소 필수)
     * 출력: 위의 3개 + EUR/KRW, EUR/JPY
     *
     * @param baseRates 모델링된 환율 맵 (키: 통화쌍 코드, 값: 환율)
     * @return 확장된 환율 맵 (원본 + 유도된 환율들)
     */
    public static Map<String, Double> triangulateRates(Map<String, Double> baseRates) {
        Objects.requireNonNull(baseRates, "baseRates must not be null");

        Map<String, Double> result = new HashMap<>(baseRates);

        Double usdKrw = baseRates.get(USD_KRW);
        Double usdJpy = baseRates.get(USD_JPY);
        Double eurUsd = baseRates.get(EUR_USD);

        if (usdKrw == null || usdJpy == null || eurUsd == null) {
            throw new IllegalArgumentException(
                "Missing required base rates: USD_KRW=%s, USD_JPY=%s, EUR_USD=%s".formatted(usdKrw, usdJpy, eurUsd)
            );
        }

        // EUR/KRW = EUR/USD × USD/KRW
        result.put("EUR_KRW", eurUsd * usdKrw);

        // EUR/JPY = EUR/USD × USD/JPY
        result.put("EUR_JPY", eurUsd * usdJpy);

        // GBP 지원 (GBP/USD가 있을 경우)
        Double gbpUsd = baseRates.get("GBP_USD");
        if (gbpUsd != null) {
            result.put("GBP_KRW", gbpUsd * usdKrw);
            result.put("GBP_JPY", gbpUsd * usdJpy);
        }

        // JPY를 기준으로 환산
        Double jpyUsd = baseRates.get("JPY_USD");
        if (jpyUsd != null) {
            result.put("EUR_JPY", eurUsd / jpyUsd);
        }

        return result;
    }

    /**
     * 두 통화쌍 환율로부터 교차 환율(cross rate) 계산.
     *
     * <p>예: USD/JPY와 USD/KRW로부터 JPY/KRW를 구한다.
     * JPY/KRW = USD/KRW ÷ USD/JPY
     *
     * @param baseRate1 기본 환율 1 (예: USD/KRW)
     * @param baseRate2 기본 환율 2 (예: USD/JPY)
     * @param reverse1 baseRate1을 역수로 취할지 여부
     * @param reverse2 baseRate2를 역수로 취할지 여부
     * @return 교차 환율
     */
    public static double calculateCrossRate(
        double baseRate1,
        double baseRate2,
        boolean reverse1,
        boolean reverse2
    ) {
        if (baseRate1 <= 0 || baseRate2 <= 0) {
            throw new IllegalArgumentException("Base rates must be positive");
        }

        double rate1 = reverse1 ? 1.0 / baseRate1 : baseRate1;
        double rate2 = reverse2 ? 1.0 / baseRate2 : baseRate2;

        return rate1 / rate2;
    }

    /**
     * 역수 환율 (reciprocal) 계산.
     *
     * @param rate 원본 환율 (예: 1200 = USD/KRW)
     * @return 역수 (예: 1/1200 = KRW/USD)
     */
    public static double invertRate(double rate) {
        if (rate <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        return 1.0 / rate;
    }
}
