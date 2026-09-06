package com.divurve.engine.concentration;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 집중도 진단 계산기 (이슈 #14, FR-XR-03, FR-FT-01).
 * 포트폴리오의 통화별 노출을 분석하여 주력 통화 비중과 임계값을 비교한다.
 * 로직: 통화별 비중을 계산하고, 최대 비중(주력 통화)을 추출하여 임계값과 비교.
 */
@EngineComponent
public class ConcentrationCalculator {

    private static final int PRECISION_SCALE = 4;
    private static final double EPSILON = 1e-10;

    /**
     * 포트폴리오의 집중도를 진단한다.
     *
     * @param currencyToAssetKrw 통화코드 → 금액(원화)의 맵
     * @param concentrationThreshold 집중도 임계값 (0 ~ 1, 투자성향별 기본값)
     * @return 집중도 진단 결과
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    public ConcentrationResult diagnose(
            Map<String, Long> currencyToAssetKrw,
            double concentrationThreshold) {
        Objects.requireNonNull(currencyToAssetKrw, "통화별 자산 맵은 null일 수 없습니다.");

        if (concentrationThreshold < 0 || concentrationThreshold > 1) {
            throw new IllegalArgumentException(
                    "집중도 임계값은 0~1 범위여야 합니다 (입력 " + concentrationThreshold + ").");
        }

        // 총 외화자산 계산
        long totalFxAssetKrw = currencyToAssetKrw.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        if (totalFxAssetKrw == 0L) {
            return new ConcentrationResult(
                    Collections.emptyMap(),
                    null,
                    0.0,
                    concentrationThreshold,
                    "safe",
                    0L
            );
        }

        // 통화별 비중 계산
        Map<String, Double> exposureMap = new LinkedHashMap<>();
        String topCurrency = null;
        double topShare = 0.0;

        for (Map.Entry<String, Long> entry : currencyToAssetKrw.entrySet()) {
            String currency = entry.getKey();
            long assetKrw = entry.getValue();

            double share = BigDecimal.valueOf(assetKrw)
                    .divide(BigDecimal.valueOf(totalFxAssetKrw), PRECISION_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();

            exposureMap.put(currency, share);

            // 주력 통화 찾기
            if (share > topShare) {
                topShare = share;
                topCurrency = currency;
            }
        }

        // 임계값 비교하여 상태 결정
        String status = topShare > concentrationThreshold ? "warning" : "safe";

        return new ConcentrationResult(
                exposureMap,
                topCurrency,
                topShare,
                concentrationThreshold,
                status,
                totalFxAssetKrw
        );
    }

    /**
     * 통화별 비중을 내림차순 정렬하여 반환한다.
     *
     * @param currencyToAssetKrw 통화코드 → 금액(원화)의 맵
     * @return 비중의 내림차순 맵
     */
    public Map<String, Double> getSortedExposure(Map<String, Long> currencyToAssetKrw) {
        Objects.requireNonNull(currencyToAssetKrw, "통화별 자산 맵은 null일 수 없습니다.");

        long totalFxAssetKrw = currencyToAssetKrw.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        if (totalFxAssetKrw == 0L) {
            return Collections.emptyMap();
        }

        // 통화별 비중을 계산하고 내림차순 정렬
        return currencyToAssetKrw.entrySet().stream()
                .map(entry -> Map.entry(
                        entry.getKey(),
                        BigDecimal.valueOf(entry.getValue())
                                .divide(BigDecimal.valueOf(totalFxAssetKrw), PRECISION_SCALE, RoundingMode.HALF_UP)
                                .doubleValue()
                ))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        Map::putAll
                );
    }

    /** 집중도 진단 결과 DTO. */
    public record ConcentrationResult(
            Map<String, Double> exposure,
            String topCurrency,
            double topShare,
            double threshold,
            String status,
            long totalFxAssetKrw
    ) {
    }
}
