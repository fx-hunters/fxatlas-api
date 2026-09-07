package com.divurve.domain.plan;

import com.divurve.engine.planner.RateRange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 계획 계산에 쓰는 환율·비용 전제 (플래너 명세 §7 "시스템이 자동으로 수집하는 데이터").
 *
 * <p>🔒 {@link #rates} 세 값은 <b>외화 1단위당 원화</b>다 (명세 §7·§21-6). JPY 100엔 고시는
 * 이미 1엔 기준으로 접혀 들어온다 — {@code CrossRateResolver} 가 내보내는 값이 항상 1단위
 * 기준이기 때문이다. {@link #quoteUnit} 은 원본 고시 단위를 기록해 둘 뿐 다시 적용되지 않는다.
 *
 * <p>🔒 방향 전망({@code model_path}·{@code forecast_factors})은 담지 않는다 — FR-FC-12 가
 * 방향 전망을 계획 계산의 입력으로 넘기는 것을 금지한다. 여기 들어오는 Forecast 값은 구간
 * ({@code interval_80})뿐이며, 그것도 "환율이 이렇게 움직인다"가 아니라 "같은 금액을 준비할 때
 * 비용이 이만큼 달라질 수 있다"를 계산하는 데만 쓰인다 (명세 §9.3).
 *
 * <p>환율을 {@code RateRange}(engine 타입)가 아니라 {@code double} 셋으로 들고 있는 것은
 * 의도적이다 — api 레이어가 이 값을 응답에 실을 때 engine 타입에 닿지 않아야 한다
 * (아키텍처 규칙: engine 은 domain 에서만 접근한다). engine 계산기에 넘길 때는
 * {@link #toRateRange()} 로 감싼다.
 *
 * @param currencyCode      목표 통화
 * @param lowRate           환율 범위 하단 (per-unit)
 * @param baseRate          기준 환율 (per-unit)
 * @param highRate          환율 범위 상단 (per-unit)
 * @param spreadRatio       스프레드 가정
 * @param feeKrw            회차당 정액 수수료 가정 (원)
 * @param quoteUnit         원본 고시 단위 (JPY 100). 기록용
 * @param minorUnits        통화 소수 자릿수 (JPY 0, 대부분 2)
 * @param rateAsOf          환율 기준 시각
 * @param forecastAsOf      Forecast 기준 시각. 구간을 못 얻었으면 {@code null}
 * @param forecastAvailable 예측 구간을 실제로 얻었는지. 거짓이면 세 환율이 모두 같다
 */
public record PlanRateContext(
        String currencyCode,
        double lowRate,
        double baseRate,
        double highRate,
        double spreadRatio,
        long feeKrw,
        int quoteUnit,
        int minorUnits,
        Instant rateAsOf,
        Instant forecastAsOf,
        boolean forecastAvailable) {

    public PlanRateContext {
        Objects.requireNonNull(currencyCode, "currencyCode");
        Objects.requireNonNull(rateAsOf, "rateAsOf");
    }

    /**
     * engine 계산기에 넘길 환율 범위.
     *
     * <p><b>domain 안에서만 호출한다.</b> api 레이어가 이 메서드를 부르면 engine 타입에 닿아
     * {@code ModuleArchitectureTest} 가 실패한다 — 그 실패는 계약이 샜다는 신호다.
     */
    public RateRange toRateRange() {
        return new RateRange(
                BigDecimal.valueOf(lowRate),
                BigDecimal.valueOf(baseRate),
                BigDecimal.valueOf(highRate));
    }

    /**
     * 예측 구간 없이 기준 환율만으로 계산한다는 경고 (명세 §20 "Forecast 없음").
     *
     * <p>이 상태에서도 계획은 만들어진다 — 명세는 "현재 환율 기준 계획과 <b>범위 계산 제한</b>
     * 표시"를 요구하지, 계산 중단을 요구하지 않는다. 다만 비용 하단·상단이 기준값과 같아지므로
     * 사용자가 범위를 실제 예측으로 오해하지 않도록 응답에 이 경고를 함께 낸다.
     */
    public static final String WARNING_FORECAST_UNAVAILABLE = "FORECAST_UNAVAILABLE";
}
