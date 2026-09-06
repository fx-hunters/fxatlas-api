package com.divurve.engine.fx;

import com.divurve.engine.EngineComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 삼각 유도 환율 계산기 (API 명세 v2 §1.4 "유도 환율", ERD §4.1·4.2 · 이슈 #57).
 *
 * <p>ECOS 는 <b>원화 크로스만</b> 고시한다 — {@code USD/KRW} · {@code JPY/KRW} · {@code EUR/KRW}.
 * 그런데 {@code /market/regime} 은 {@code USDKRW}·{@code USDJPY}·{@code EURUSD} 세 쌍의 국면을
 * 요구한다(명세 §5.10). 뒤의 두 쌍은 고시가 없으므로 두 원화 크로스의 비로 유도한다.
 *
 * <pre>
 *   USD/JPY = (USD/KRW) ÷ (JPY/KRW)
 *   EUR/USD = (EUR/KRW) ÷ (USD/KRW)
 * </pre>
 *
 * <p><b>같은 날짜에 두 계열이 모두 있는 날만</b> 결과에 넣는다. 한쪽만 있는 날을 앞뒤 값으로
 * 메우면 없는 관측을 만들어내는 것이고(FR-CM-10), 그렇게 채운 날은 수익률이 0 이 되어
 * 변동성을 실제보다 낮게 만든다.
 *
 * <p>입력은 <b>1통화 단위 기준</b>이어야 한다 — ECOS 의 원/100엔 고시는 호출 전에
 * {@code QuoteUnitNormalizer} 로 접는다. 접지 않은 값을 넣으면 USD/JPY 가 100배로 나온다.
 */
@EngineComponent
public class CrossRateDeriver {

    /**
     * 날짜가 붙은 환율 한 점.
     *
     * @param date 기준일
     * @param rate 환율 (1통화 단위 기준)
     */
    public record DatedRate(LocalDate date, double rate) {
    }

    /**
     * 두 원화 크로스에서 교차 환율 계열을 유도한다.
     *
     * @param baseKrw  분자 계열 — 기준통화의 원화 환율 (예 {@code USD/KRW})
     * @param quoteKrw 분모 계열 — 표시통화의 원화 환율 (예 {@code JPY/KRW})
     * @return 두 계열에 모두 존재하는 날짜의 교차 환율, 날짜 오름차순.
     *         분모가 0 이하인 날은 계산할 수 없으므로 제외한다
     */
    public List<DatedRate> derive(List<DatedRate> baseKrw, List<DatedRate> quoteKrw) {
        Objects.requireNonNull(baseKrw, "baseKrw must not be null");
        Objects.requireNonNull(quoteKrw, "quoteKrw must not be null");

        Map<LocalDate, Double> denominator = new LinkedHashMap<>();
        for (DatedRate point : quoteKrw) {
            denominator.put(point.date(), point.rate());
        }

        List<DatedRate> derived = new ArrayList<>();
        for (DatedRate point : baseKrw) {
            Double divisor = denominator.get(point.date());
            if (divisor == null || divisor <= 0) {
                continue;
            }
            derived.add(new DatedRate(point.date(), point.rate() / divisor));
        }
        derived.sort(java.util.Comparator.comparing(DatedRate::date));
        return derived;
    }
}
