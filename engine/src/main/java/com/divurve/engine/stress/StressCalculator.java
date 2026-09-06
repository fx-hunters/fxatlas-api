package com.divurve.engine.stress;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 스트레스 테스트 계산기 (요구사항 §4.8 FR-ST-01~05, API 명세 v2 §5.9).
 *
 * <h2>이 계산은 예측이 아니다</h2>
 * 여기서 나오는 수치는 <b>입력한 가정 충격값에 대한 조건부 산술</b>이다. 미래 확률도, 방향 전망도 아니다.
 * 그래서 결과에는 충격값·적용 순서·해석 코드만 담고, 확률이나 권유 문구는 담지 않는다.
 *
 * <h2>적용 순서 — 주가 충격 먼저, 그 다음 환율 충격</h2>
 * <pre>
 *   equity_effect = 해외주식평가액 × equity_shock_pct
 *   fx_effect     = (외화자산 + equity_effect) × fx_shock_pct
 *   total_effect  = equity_effect + fx_effect
 * </pre>
 * 순서를 고정해야 {@code equity_effect + fx_effect = total_effect} 가 <b>정확히</b> 성립하고,
 * ERD {@code stress_test_runs} 의 3컬럼 구조와도 일치한다(명세 §5.9). 환율 충격을 원래 외화자산에
 * 걸면 두 효과가 겹쳐 합이 어긋난다.
 *
 * <p>명세 §4 fixture 검산 — 해외주식 20,000,000 · 외화자산 24,720,000 에
 * {@code equity_shock_pct = -0.20}, {@code fx_shock_pct = +0.10} 을 적용하면
 * 주가 -4,000,000 / 환율 +2,072,000 / 합계 -1,928,000, 적용 후 22,792,000 이다.
 *
 * <h2>부호 규약 (FR-CM-05)</h2>
 * {@code fx_shock_pct > 0} = USD/KRW 상승 = 원화 약세 = 외화자산 원화 평가액 <b>증가</b>.
 * 전 응답에서 동일하다.
 *
 * <h2>변경 이력 (calc)</h2>
 * v1 은 통화별 환율 충격만 적용해 fixture 에서 {@code +2,472,000}(환율 단독) 을 냈고,
 * 주가 충격도 총 평가금액 효과 분리도 없었다. 요구사항 §4.8 이 요구하는 3항 분리로 다시 구현했다.
 */
@EngineComponent
public class StressCalculator {

    /** 주가 손실을 환율 효과가 일부만 상쇄해 총효과가 여전히 손실인 경우. */
    public static final String FX_CUSHIONS_EQUITY_LOSS = "fx_cushions_equity_loss";

    /** 주가 손실을 환율 효과가 전부 상쇄해 총효과가 손실이 아닌 경우. */
    public static final String FX_OFFSETS_EQUITY_LOSS = "fx_offsets_equity_loss";

    /** 주가와 환율 효과가 모두 손실 방향인 경우 (주가 하락 + 원화 강세). */
    public static final String EQUITY_AND_FX_BOTH_NEGATIVE = "equity_and_fx_both_negative";

    /** 주가 효과는 손실이 아닌데 환율 효과가 깎는 경우. */
    public static final String FX_REDUCES_EQUITY_GAIN = "fx_reduces_equity_gain";

    /** 주가·환율 효과가 모두 손실이 아닌 경우. */
    public static final String EQUITY_AND_FX_BOTH_POSITIVE = "equity_and_fx_both_positive";

    /** 충격률 허용 범위 하한 — -1.0(전액 소멸) 미만은 의미가 없다. */
    private static final double SHOCK_MIN = -1.0;

    /** 충격률 허용 범위 상한 — 가정 충격이라도 +10.0(1000퍼센트) 을 넘으면 입력 오류로 본다. */
    private static final double SHOCK_MAX = 10.0;

    /**
     * 시나리오 충격을 순서대로 적용한다 (주가 → 환율).
     *
     * @param equityAssetKrw 해외주식 평가액(원화). 환율 충격 전 기준값
     * @param fxAssetKrw     외화자산 전체 평가액(원화). 해외주식 + 외화예금
     * @param equityShockPct 주가 충격률 (예 {@code -0.20} = 20퍼센트 하락)
     * @param fxShockPct     환율 충격률 (예 {@code 0.10} = 원화 10퍼센트 약세)
     * @return 효과 3항과 적용 전/후 외화자산
     * @throws IllegalArgumentException 자산이 음수이거나, 해외주식이 외화자산보다 크거나,
     *                                  충격률이 범위를 벗어나거나 NaN 인 경우
     */
    public ScenarioResult applyScenario(
            long equityAssetKrw,
            long fxAssetKrw,
            double equityShockPct,
            double fxShockPct) {
        if (equityAssetKrw < 0L) {
            throw new IllegalArgumentException(
                    "해외주식 평가액은 음수일 수 없습니다 (입력 " + equityAssetKrw + ").");
        }
        if (fxAssetKrw < 0L) {
            throw new IllegalArgumentException(
                    "외화자산 평가액은 음수일 수 없습니다 (입력 " + fxAssetKrw + ").");
        }
        if (equityAssetKrw > fxAssetKrw) {
            throw new IllegalArgumentException(
                    "해외주식 평가액(" + equityAssetKrw + ")은 외화자산 평가액("
                            + fxAssetKrw + ")보다 클 수 없습니다.");
        }
        validateShock(equityShockPct, "equity_shock_pct");
        validateShock(fxShockPct, "fx_shock_pct");

        // 1) 주가 충격 — 해외주식 평가액에만 적용한다.
        long equityEffectKrw = roundKrw(BigDecimal.valueOf(equityAssetKrw)
                .multiply(BigDecimal.valueOf(equityShockPct)));

        // 2) 환율 충격 — 주가 충격이 반영된 외화자산 전체에 적용한다.
        long fxBaseKrw = fxAssetKrw + equityEffectKrw;
        long fxEffectKrw = roundKrw(BigDecimal.valueOf(fxBaseKrw)
                .multiply(BigDecimal.valueOf(fxShockPct)));

        // 3) 총 평가금액 효과 — 두 항의 합이 곧 총효과다(적용 순서 고정의 결과).
        long totalEffectKrw = equityEffectKrw + fxEffectKrw;
        long fxAssetAfterKrw = fxAssetKrw + totalEffectKrw;

        return new ScenarioResult(
                equityAssetKrw,
                fxAssetKrw,
                equityShockPct,
                fxShockPct,
                equityEffectKrw,
                fxEffectKrw,
                totalEffectKrw,
                fxAssetAfterKrw,
                interpret(equityEffectKrw, fxEffectKrw, totalEffectKrw)
        );
    }

    private static void validateShock(double shockPct, String field) {
        if (Double.isNaN(shockPct) || shockPct < SHOCK_MIN || shockPct > SHOCK_MAX) {
            throw new IllegalArgumentException(
                    field + " 는 " + SHOCK_MIN + " 이상 " + SHOCK_MAX + " 이하여야 합니다 (입력 " + shockPct + ").");
        }
    }

    private static long roundKrw(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 두 효과의 관계를 해석 코드로 옮긴다. 문장은 클라이언트가 코드로 고르며, 여기서는 사실만 분류한다
     * — 어떤 행동을 하라는 표현을 서버가 만들지 않기 위해서다(요구사항 §2.2).
     */
    private static String interpret(long equityEffectKrw, long fxEffectKrw, long totalEffectKrw) {
        if (equityEffectKrw < 0L) {
            if (fxEffectKrw <= 0L) {
                return EQUITY_AND_FX_BOTH_NEGATIVE;
            }
            return totalEffectKrw < 0L ? FX_CUSHIONS_EQUITY_LOSS : FX_OFFSETS_EQUITY_LOSS;
        }
        return fxEffectKrw < 0L ? FX_REDUCES_EQUITY_GAIN : EQUITY_AND_FX_BOTH_POSITIVE;
    }

    /**
     * 시나리오 적용 결과 (명세 §5.9 {@code shock} · {@code before} · {@code effects} · {@code after}).
     *
     * @param equityAssetKrw      적용 전 해외주식 평가액
     * @param fxAssetBeforeKrw    적용 전 외화자산 평가액
     * @param equityShockPct      적용한 주가 충격률 (실행 시점 스냅샷)
     * @param fxShockPct          적용한 환율 충격률 (실행 시점 스냅샷)
     * @param equityEffectKrw     주가 효과
     * @param fxEffectKrw         환율 효과
     * @param totalEffectKrw      총 평가금액 효과 (= 주가 + 환율)
     * @param fxAssetAfterKrw     적용 후 외화자산 평가액
     * @param interpretationCode  두 효과의 관계 코드
     */
    public record ScenarioResult(
            long equityAssetKrw,
            long fxAssetBeforeKrw,
            double equityShockPct,
            double fxShockPct,
            long equityEffectKrw,
            long fxEffectKrw,
            long totalEffectKrw,
            long fxAssetAfterKrw,
            String interpretationCode
    ) {
    }

    /**
     * 통화별 환율 충격만 적용하는 v1 계산 (이슈 #14).
     *
     * @deprecated 요구사항 §4.8 이 요구하는 주가 충격·총액 효과 분리가 없어
     *     {@link #applyScenario(long, long, double, double)} 로 대체됐다. 유일한 호출처인
     *     {@code XrayService.applyStress}({@code POST /xray/stress}) 가 명세 §0.1 에 따라
     *     삭제되면 이 메서드도 함께 지운다.
     *
     * @param currencyToAssetLocalCurrency 통화코드 → 자산(로컬 통화)의 맵
     * @param currencyToRateKrw            통화코드 → 환율(원화/외통)의 맵
     * @param currencyToShock              통화코드 → 환율 충격률(음수 가능)의 맵
     * @return 스트레스 계산 결과
     * @throws IllegalArgumentException 입력값이 부적절한 경우
     */
    @Deprecated(forRemoval = true)
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

            BigDecimal valueBeforeKrw = BigDecimal.valueOf(assetLocal)
                    .multiply(rateKrw)
                    .setScale(0, RoundingMode.HALF_UP);

            BigDecimal shockFactor = BigDecimal.ONE.add(BigDecimal.valueOf(shock));
            BigDecimal rateAfterKrw = rateKrw.multiply(shockFactor)
                    .setScale(4, RoundingMode.HALF_UP);

            BigDecimal valueAfterKrw = BigDecimal.valueOf(assetLocal)
                    .multiply(rateAfterKrw)
                    .setScale(0, RoundingMode.HALF_UP);

            long impactKrw = valueAfterKrw.subtract(valueBeforeKrw).longValue();

            totalBeforeKrw += valueBeforeKrw.longValue();
            totalAfterKrw += valueAfterKrw.longValue();

            impacts.put(currency, new CurrencyStressImpact(currency, shock, impactKrw));
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

    /**
     * v1 스트레스 테스트 결과 DTO.
     *
     * @deprecated {@link #apply} 와 함께 삭제 예정.
     */
    @Deprecated(forRemoval = true)
    public record StressResult(
            long totalAssetBeforeKrw,
            long totalAssetAfterKrw,
            long portfolioImpactKrw,
            double portfolioImpactRatio,
            Map<String, CurrencyStressImpact> byCurrencyMap
    ) {
    }

    /**
     * v1 통화별 스트레스 영향 DTO.
     *
     * @deprecated {@link #apply} 와 함께 삭제 예정.
     */
    @Deprecated(forRemoval = true)
    public record CurrencyStressImpact(
            String currencyCode,
            double shock,
            long impactKrw
    ) {
    }
}
