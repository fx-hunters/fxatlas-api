package com.divurve.engine.weight;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * 고시 단위 정규화기 (ERD v3.0 §4.1 {@code currencies.quote_unit}).
 *
 * <p>ECOS 는 JPY 를 <b>원/100엔</b>으로 고시한다. ERD 는 "저장과 전송은 1엔 기준, 100엔 환산은
 * 표시 단계에서"를 규정하므로(API 명세 v2 §1.4), 외부 환율을 도메인으로 들일 때 1단위 환율로 접는다.
 *
 * <p>이 정규화가 {@code PurchaseFxRateResolver} 안에만 있어서, {@code XrayService}·{@code FitService}
 * 는 원값을 그대로 곱했다 — <b>JPY 자산이 100배로 잡혀</b> 외화 비중·집중도·민감도·스트레스가 모두
 * 오염됐다(예: JPY 예금 500,000엔이 4,695,650원이 아니라 469,565,000원). 세 곳이 같은 순수 함수를
 * 쓰도록 engine 으로 끌어올린 것이 이 클래스다.
 */
@EngineComponent
public class QuoteUnitNormalizer {

    /** 100 단위로 고시되는 통화. */
    private static final Set<String> PER_HUNDRED_UNITS = Set.of("JPY");

    /** 100 단위 고시 통화의 환산 제수. */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 외부 고시 환율을 1통화 단위당 원화 환율로 정규화한다.
     *
     * @param currencyCode 통화코드 (대소문자 무관)
     * @param quotedRate   외부 고시 환율 (JPY 는 원/100엔)
     * @return 1단위당 원화 환율. 100 단위 통화가 아니면 입력을 그대로 돌려준다
     */
    public BigDecimal toPerUnitRate(String currencyCode, BigDecimal quotedRate) {
        Objects.requireNonNull(currencyCode, "통화코드는 null일 수 없습니다.");
        Objects.requireNonNull(quotedRate, "고시 환율은 null일 수 없습니다.");

        if (isQuotedPerHundred(currencyCode)) {
            return quotedRate.divide(HUNDRED);
        }
        return quotedRate;
    }

    /**
     * 해당 통화가 100 단위로 고시되는지 여부.
     *
     * @param currencyCode 통화코드 (대소문자 무관)
     * @return 100 단위 고시면 {@code true}
     */
    public boolean isQuotedPerHundred(String currencyCode) {
        Objects.requireNonNull(currencyCode, "통화코드는 null일 수 없습니다.");
        return PER_HUNDRED_UNITS.contains(currencyCode.toUpperCase());
    }
}
