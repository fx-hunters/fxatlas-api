package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.fx.PerUnitFxRates;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 보유 종목·외화 예금을 <b>통화별 원화 평가액</b>으로 환산한다 (FR-XR-01 · FR-XR-02).
 *
 * <p>{@code XrayService} 와 {@code FitService} 가 같은 환산을 각자 복사해 갖고 있었고, 둘 다
 * ECOS 의 <b>원/100엔</b> 고시를 그대로 곱해 <b>JPY 자산을 100배</b>로 잡았다(비중·집중도·민감도가 모두 오염).
 * 환산을 여기 한 곳으로 모으고 {@link QuoteUnitNormalizer} 로 1단위 환율에 접는다(ERD §4.1, 명세 §1.4).
 *
 * <p>결과 맵은 <b>원화 평가액 내림차순</b>이다 — 명세 §5.3 {@code exposure} 예시 순서(USD·JPY·EUR)와 같고,
 * 집중도 진단·민감도 응답이 입력 순서를 그대로 물려받아 결정론적으로 나온다.
 */
@UseCase
public class FxAssetValuator {

    private final PerUnitFxRates perUnitFxRates;

    public FxAssetValuator(PerUnitFxRates perUnitFxRates) {
        this.perUnitFxRates = Objects.requireNonNull(perUnitFxRates, "perUnitFxRates is null");
    }

    /**
     * 보유 종목과 외화 예금을 통화별 원화 평가액으로 환산한다.
     *
     * @param holdings 보유 종목 (평가액 = 수량 × 평균단가, 현재가 피드가 없어 매입가 기준)
     * @param deposits 외화 예금
     * @return 통화별 평가액과 그때 쓴 1단위 환율
     */
    public FxValuation valuate(List<Holding> holdings, List<Deposit> deposits) {
        Objects.requireNonNull(holdings, "holdings is null");
        Objects.requireNonNull(deposits, "deposits is null");

        Map<String, BigDecimal> rates = fetchPerUnitRates(holdings, deposits);
        Map<String, Long> assets = new HashMap<>();

        for (Holding holding : holdings) {
            BigDecimal rate = rates.get(holding.getCurrencyCode());
            if (rate != null) {
                assets.merge(
                        holding.getCurrencyCode(),
                        toKrw(BigDecimal.valueOf(holding.getQuantity() * holding.getAvgPrice()), rate),
                        Long::sum);
            }
        }

        for (Deposit deposit : deposits) {
            BigDecimal rate = rates.get(deposit.getCurrencyCode());
            if (rate != null) {
                assets.merge(deposit.getCurrencyCode(), toKrw(deposit.getAmount(), rate), Long::sum);
            }
        }

        return new FxValuation(sortByAmountDesc(assets), rates);
    }

    /**
     * 통화별 현재 환율을 1통화 단위 기준으로 조회한다.
     *
     * @param holdings 보유 종목
     * @param deposits 외화 예금
     * @return 통화코드 → 1단위당 원화 환율. 조회에 실패한 통화는 담기지 않는다
     */
    public Map<String, BigDecimal> fetchPerUnitRates(List<Holding> holdings, List<Deposit> deposits) {
        Map<String, BigDecimal> rates = new HashMap<>();
        holdings.forEach(holding -> putRate(rates, holding.getCurrencyCode()));
        deposits.forEach(deposit -> putRate(rates, deposit.getCurrencyCode()));
        return rates;
    }

    /**
     * 통화 하나의 환율을 채운다. 조회 실패는 그 통화만 빠뜨리고 나머지는 그대로 계산한다.
     *
     * <p>판정과 로깅은 {@link PerUnitFxRates#find} 한 곳에서 한다 — 예전에는 이 로직이
     * {@code ForecastService}·{@code StressRunService} 에도 복사돼 있었고, 사본마다 처리가 달라
     * 같은 GBP 보유 상태에서 {@code /xray} 는 200, {@code /stress/runs} 는 400 이었다(이슈 #57).
     */
    private void putRate(Map<String, BigDecimal> rates, String currencyCode) {
        if (rates.containsKey(currencyCode)) {
            return;
        }
        perUnitFxRates.find(currencyCode).ifPresent(rate -> rates.put(currencyCode, rate));
    }

    /** 원화는 정수다(ERD 설계원칙) — 절사가 아니라 반올림한다. */
    private long toKrw(BigDecimal amount, BigDecimal perUnitRateKrw) {
        return amount.multiply(perUnitRateKrw).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Map<String, Long> sortByAmountDesc(Map<String, Long> assets) {
        Map<String, Long> sorted = new LinkedHashMap<>();
        assets.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    /**
     * 외화자산 환산 결과.
     *
     * @param currencyToAssetKrw 통화코드 → 원화 평가액 (평가액 내림차순)
     * @param currencyToRateKrw  통화코드 → 1단위당 원화 환율 (JPY 는 100엔 고시를 접은 값)
     */
    public record FxValuation(
            Map<String, Long> currencyToAssetKrw,
            Map<String, BigDecimal> currencyToRateKrw) {

        /** 외화자산 총액. */
        public long fxAssetKrw() {
            return currencyToAssetKrw.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
