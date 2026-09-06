package com.divurve.engine.volatility;

import com.divurve.engine.EngineComponent;

/**
 * 변동성 백분위 → 국면({@link Regime}) 분류 (FR-SF-02).
 *
 * <p>입력은 ERD {@code fx_stats.vol_percentile_5y}({@code NUMERIC(5,4)}) 와 같은 단위인
 * <b>0~1 비율</b>이다. API 명세 §1.4 "비율은 0과 1 사이 소수"와도 일치한다.
 * 0~100 정수를 넘기면 {@link IllegalArgumentException} 이다 — 단위 혼동을 조용히 흡수하지 않는다.
 *
 * <p>순수 함수다. 시각·난수·외부 상태에 의존하지 않으므로 같은 입력은 항상 같은 국면을 낸다.
 *
 * <h2>⚠️ 경계값은 문서 미확정 — 팀 확정 필요</h2>
 * 요구사항 v2 · API 명세 v2 · ERD v3.0 어디에도 국면 경계 수치가 명시돼 있지 않다.
 * 아래 기본값은 <b>명세에 실린 예시값 3개를 모두 만족하는</b> 최소 가정이다.
 * <table border="1">
 *   <caption>명세 예시값과의 정합</caption>
 *   <tr><th>출처</th><th>vol_percentile_5y</th><th>명세가 적은 regime</th><th>이 경계값의 결과</th></tr>
 *   <tr><td>§5.10 EURUSD</td><td>0.22</td><td>calm</td><td>calm</td></tr>
 *   <tr><td>§5.10 USDJPY</td><td>0.41</td><td>normal</td><td>normal</td></tr>
 *   <tr><td>§5.7 · §5.10 USDKRW</td><td>0.72</td><td>elevated</td><td>elevated</td></tr>
 * </table>
 * {@code stress} 경계(0.90)는 예시가 없어 순수 가정이다. 팀 확정 시 이 상수와
 * {@code RegimeClassifierTest} 의 경계 테스트를 함께 고친다.
 */
@EngineComponent
public class RegimeClassifier {

    /** 이 값 이상부터 {@link Regime#NORMAL} (미만은 {@code calm}). ⚠️ 문서 미확정 — 팀 확정 필요. */
    public static final double NORMAL_MIN_PERCENTILE = 0.25;

    /** 이 값 이상부터 {@link Regime#ELEVATED}. ⚠️ 문서 미확정 — 팀 확정 필요. */
    public static final double ELEVATED_MIN_PERCENTILE = 0.70;

    /** 이 값 이상부터 {@link Regime#STRESS}. ⚠️ 문서 미확정 — 팀 확정 필요. */
    public static final double STRESS_MIN_PERCENTILE = 0.90;

    /**
     * 5년 변동성 백분위를 국면으로 분류한다.
     *
     * <p>경계는 모두 <b>하한 포함·상한 미포함</b>이다.
     * 정확히 0.25 는 {@code normal}, 0.70 은 {@code elevated}, 0.90 은 {@code stress} 다.
     *
     * @param volPercentile5y 5년 변동성 백분위, 0~1 비율 (ERD {@code fx_stats.vol_percentile_5y})
     * @return 국면
     * @throws IllegalArgumentException 0~1 범위를 벗어나거나 NaN 인 경우
     */
    public Regime classify(double volPercentile5y) {
        if (Double.isNaN(volPercentile5y)) {
            throw new IllegalArgumentException("vol_percentile_5y 가 NaN 입니다.");
        }
        if (volPercentile5y < 0.0 || volPercentile5y > 1.0) {
            throw new IllegalArgumentException(
                "vol_percentile_5y 는 0~1 비율이어야 합니다(0~100 정수 아님): %s".formatted(volPercentile5y));
        }

        if (volPercentile5y >= STRESS_MIN_PERCENTILE) {
            return Regime.STRESS;
        }
        if (volPercentile5y >= ELEVATED_MIN_PERCENTILE) {
            return Regime.ELEVATED;
        }
        if (volPercentile5y >= NORMAL_MIN_PERCENTILE) {
            return Regime.NORMAL;
        }
        return Regime.CALM;
    }
}
