package com.divurve.domain.master;

import java.util.List;

/**
 * 지원 통화 표시 규칙 마스터 (이슈 #11). 통화별 소수 자릿수·호가 단위·USD 페어에서의 USD 위치·색상 토큰을
 * 은행/채널과 무관하게 고정 제공한다. 여기서는 <b>데이터 조회만</b> 한다 — 계산 로직은 없다.
 *
 * <p>값은 해커톤 M0 표본이며 실제 고시 규칙 연동 시 대체한다. 표시 규칙일 뿐이므로
 * 이 값들은 비용계산(FR-RT-11)의 입력이 아니라 프론트 표시 포맷의 근거다.
 */
public final class CurrencyMaster {

    // 표시 순서를 보존하기 위해 List 로 둔다 (KRW 는 자국 통화이므로 지원 외화 목록에서 제외).
    private static final List<Currency> CURRENCIES = List.of(
            new Currency("USD", 2, 1, "base", "currency-usd"),
            new Currency("EUR", 2, 1, "quote", "currency-eur"),
            new Currency("JPY", 0, 100, "base", "currency-jpy"),
            new Currency("GBP", 2, 1, "quote", "currency-gbp"),
            new Currency("CNY", 2, 1, "base", "currency-cny"));

    private CurrencyMaster() {
    }

    /** 지원 통화 표시 규칙 전체를 명세 표시 순서대로 반환한다. */
    public static List<Currency> all() {
        return CURRENCIES;
    }

    /**
     * 통화별 표시 규칙.
     *
     * @param currencyCode ISO 4217 통화 코드
     * @param minorUnits   소수 자릿수 (JPY=0, 대부분 2)
     * @param quoteUnit    호가 단위 (JPY 는 100 단위 호가)
     * @param usdSide      USD 페어에서 USD 의 위치 (base/quote)
     * @param colorToken   프론트 색상 디자인 토큰
     */
    public record Currency(
            String currencyCode,
            int minorUnits,
            int quoteUnit,
            String usdSide,
            String colorToken) {
    }
}
