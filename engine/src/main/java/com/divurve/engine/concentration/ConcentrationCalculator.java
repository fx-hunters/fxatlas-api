package com.divurve.engine.concentration;

import com.divurve.engine.EngineComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 계획 완료 후 통화 노출·외화 비중 변화 시뮬레이션 (FR-RT-16/17).
 *
 * 계산 대상:
 * - 통화별 외화액 비중
 * - 각 외화 내 집중도 (Herfindahl Index)
 *
 * 판정 기준:
 * - worsens: 집중도 증가
 * - improves: 집중도 감소
 * - neutral: 변화 미미 (임계값 내)
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
}
