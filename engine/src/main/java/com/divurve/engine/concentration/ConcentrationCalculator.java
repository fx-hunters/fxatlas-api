package com.divurve.engine.concentration;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 통화 집중도 계산기. 이슈 #14(X-Ray/Fit)와 #18(계획 미리보기)이 각각 필요로 한 두 계열의 계산을 담는다.
 *
 * <p>진단 계열 (이슈 #14, FR-XR-03, FR-FT-01) — {@link #diagnose}, {@link #getSortedExposure}:
 * 포트폴리오의 통화별 노출을 분석해 주력 통화 비중을 추출하고 임계값과 비교한다.
 * 초과 시 {@code warning}, 이하면 {@code safe}.
 *
 * <p>변화 보고 계열 (FR-RT-16/17) — {@link #calculateConcentration},
 * {@link #verdictConcentrationChange}, {@link #report}:
 * 계획 완료 전후의 통화 노출 변화를 시뮬레이션한다. 집중도는 Herfindahl Index 로 계산하고,
 * 변화는 {@code worsens}(증가) / {@code improves}(감소) / {@code neutral}(임계값 내 미미)로 판정한다.
 */
@EngineComponent
public class ConcentrationCalculator {

    private static final double THRESHOLD = 0.02; // 2% 이내 변화는 neutral

    /**
     * 외화 보유 현황의 집중도를 계산한다.
     *
     * @param holdingsByCode 통화별 보유액 (Map: 통화코드 -> 금액)
     * @return 집중도 (0.0~1.0, 높을수록 집중)
     */
    public double calculateConcentration(Map<String, Double> holdingsByCode) {
        Objects.requireNonNull(holdingsByCode, "보유액 맵은 null일 수 없습니다");

        if (holdingsByCode.isEmpty()) {
            return 0.0;
        }

        double total = holdingsByCode.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (total <= 0.0) {
            return 0.0;
        }

        double herfindahlIndex = holdingsByCode.values().stream()
                .mapToDouble(amount -> (amount / total) * (amount / total))
                .sum();

        return herfindahlIndex;
    }

    /**
     * 계획 후 집중도 변화를 판정한다.
     *
     * @param beforeConcentration 계획 전 집중도
     * @param afterConcentration 계획 후 집중도
     * @return verdict: "worsens" / "improves" / "neutral"
     */
    public String verdictConcentrationChange(double beforeConcentration, double afterConcentration) {
        double delta = afterConcentration - beforeConcentration;

        if (Math.abs(delta) <= THRESHOLD) {
            return "neutral";
        }

        return delta > 0.0 ? "worsens" : "improves";
    }

    /**
     * 현황과 예상을 비교하여 집중도 변화 보고를 생성한다.
     *
     * @param beforeHoldings 계획 전 통화별 보유액
     * @param afterHoldings 계획 후 예상 통화별 보유액
     * @return 변화 보고 정보 (before, after, threshold, verdict)
     */
    public ConcentrationReport report(Map<String, Double> beforeHoldings,
            Map<String, Double> afterHoldings) {
        Objects.requireNonNull(beforeHoldings, "계획 전 보유액 맵은 null일 수 없습니다");
        Objects.requireNonNull(afterHoldings, "계획 후 보유액 맵은 null일 수 없습니다");

        double beforeConcentration = calculateConcentration(beforeHoldings);
        double afterConcentration = calculateConcentration(afterHoldings);
        String verdict = verdictConcentrationChange(beforeConcentration, afterConcentration);

        return new ConcentrationReport(
                normalizeHoldings(beforeHoldings),
                normalizeHoldings(afterHoldings),
                THRESHOLD,
                verdict
        );
    }

    private Map<String, Double> normalizeHoldings(Map<String, Double> holdings) {
        if (holdings.isEmpty()) {
            return new HashMap<>();
        }

        double total = holdings.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : holdings.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / total);
        }

        return normalized;
    }

    /**
     * 집중도 변화 보고.
     */
    public record ConcentrationReport(
            Map<String, Double> before,
            Map<String, Double> after,
            double threshold,
            String verdict
    ) {
    }

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
