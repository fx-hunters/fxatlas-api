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
 * 포트폴리오의 통화별 노출을 분석해 주력 통화 비중을 추출하고 성향별 기준선과 비교한다.
 * 상태 어휘는 {@code above_threshold} / {@code within_threshold} / {@code unknown}(API 명세 v2 §5.3).
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

    /** 주력 통화 비중이 기준선을 넘은 상태 (API 명세 v2 §5.3). */
    public static final String ABOVE_THRESHOLD = "above_threshold";
    /** 주력 통화 비중이 기준선 이내인 상태. */
    public static final String WITHIN_THRESHOLD = "within_threshold";
    /** 판정할 수 없는 상태 — 성향 미측정(기준선 없음) 또는 외화자산 없음. */
    public static final String UNKNOWN = "unknown";

    /**
     * 포트폴리오의 통화 집중도를 진단한다 (FR-XR-03 · FR-FT-01).
     *
     * <p>상태 어휘는 API 명세 v2 §5.3 을 따른다 — {@code above_threshold} / {@code within_threshold} /
     * {@code unknown}. v1 의 {@code warning}/{@code safe} 는 판정을 가치평가처럼 읽히게 해 교체했다.
     *
     * <p>기준선이 {@code null}(성향 미측정)이면 임의의 기본값을 채우지 않고 {@code unknown} 을 낸다
     * (FR-IS-06). 외화자산이 0 이어도 마찬가지로 {@code unknown} 이며 주력 통화·비중은 {@code null} 이다
     * — 0 으로 그린 차트가 아니라 빈 상태를 클라이언트가 그린다(FR-CM-09).
     *
     * <p>기준선 비교는 <b>반올림 전 정확한 비중</b>으로 한다. 이전 구현은 4자리 반올림 후 비교해
     * 0.60004 가 0.6000 으로 접혀 기준선 초과를 놓쳤다. 응답에 싣는 {@code topShare}·{@code exposure}
     * 만 4자리로 반올림한다(명세 §1.4 비율 표기).
     *
     * @param currencyToAssetKrw     통화코드 → 금액(원화)의 맵
     * @param concentrationThreshold 성향별 집중도 기준선(0~1). 미측정이면 {@code null}
     * @return 집중도 진단 결과
     * @throws IllegalArgumentException 기준선이 0~1 범위를 벗어난 경우
     */
    public ConcentrationResult diagnose(
            Map<String, Long> currencyToAssetKrw,
            Double concentrationThreshold) {
        Objects.requireNonNull(currencyToAssetKrw, "통화별 자산 맵은 null일 수 없습니다.");

        if (concentrationThreshold != null
                && (concentrationThreshold < 0 || concentrationThreshold > 1)) {
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
                    null,
                    concentrationThreshold,
                    null,
                    UNKNOWN,
                    0L
            );
        }

        // 통화별 비중 계산
        Map<String, Double> exposureMap = new LinkedHashMap<>();
        String topCurrency = null;
        double topExactShare = -1.0;
        double topShare = 0.0;

        for (Map.Entry<String, Long> entry : currencyToAssetKrw.entrySet()) {
            String currency = entry.getKey();
            long assetKrw = entry.getValue();

            double exactShare = (double) assetKrw / totalFxAssetKrw;
            double share = BigDecimal.valueOf(assetKrw)
                    .divide(BigDecimal.valueOf(totalFxAssetKrw), PRECISION_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();

            exposureMap.put(currency, share);

            // 주력 통화 찾기 — 반올림 전 값으로 비교한다.
            if (exactShare > topExactShare) {
                topExactShare = exactShare;
                topShare = share;
                topCurrency = currency;
            }
        }

        String status = concentrationThreshold == null
                ? UNKNOWN
                : (topExactShare > concentrationThreshold ? ABOVE_THRESHOLD : WITHIN_THRESHOLD);

        Double gapPp = concentrationThreshold == null
                ? null
                : BigDecimal.valueOf(topShare - concentrationThreshold)
                        .setScale(PRECISION_SCALE, RoundingMode.HALF_UP)
                        .doubleValue();

        return new ConcentrationResult(
                exposureMap,
                topCurrency,
                topShare,
                concentrationThreshold,
                gapPp,
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

    /**
     * 집중도 진단 결과 DTO.
     *
     * @param topCurrency 주력 통화. 외화자산이 없으면 {@code null}
     * @param topShare    주력 통화 비중(0~1, 4자리). 외화자산이 없으면 {@code null}
     * @param threshold   성향별 기준선. 성향 미측정이면 {@code null}
     * @param gapPp       주력 통화 비중 − 기준선 (명세 §5.5 {@code relation.facts.gap_pp}).
     *                    기준선이나 주력 통화가 없으면 {@code null}
     * @param status      {@link #ABOVE_THRESHOLD} / {@link #WITHIN_THRESHOLD} / {@link #UNKNOWN}
     */
    public record ConcentrationResult(
            Map<String, Double> exposure,
            String topCurrency,
            Double topShare,
            Double threshold,
            Double gapPp,
            String status,
            long totalFxAssetKrw
    ) {
    }
}
