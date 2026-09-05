package com.divurve.domain.settings;

import java.util.Map;

/**
 * 은행별 기본 환전 스프레드 마스터 (이슈 #10, FR-MY-04). 실효 스프레드 계산의 입력이 되는
 * 은행 고시 기본 스프레드 비율을 은행 코드로 조회한다.
 *
 * <p>값은 USD 현찰 기준 대표 스프레드다(해커톤 M0 표본). 실제 고시 스프레드 연동(ECOS/은행 API)은 이후 대체하며,
 * 미등록 은행 코드나 미지정({@code null})은 {@link #DEFAULT_BASE_SPREAD_RATIO} 를 쓴다.
 * 여기서는 <b>데이터 조회만</b> 한다 — 스프레드에 우대율을 적용하는 계산은 engine {@code EffectiveSpreadCalculator} 가 한다.
 */
public final class BankSpreadTable {

    /** 미등록/미지정 은행에 적용하는 기본 스프레드 비율. */
    public static final double DEFAULT_BASE_SPREAD_RATIO = 0.0175;

    // 은행 코드(금융기관 표준코드) → USD 현찰 기준 대표 기본 스프레드 비율.
    private static final Map<String, Double> BASE_SPREAD_BY_BANK = Map.of(
            "004", 0.0175, // KB국민
            "088", 0.0170, // 신한
            "020", 0.0180, // 우리
            "081", 0.0165, // 하나
            "011", 0.0175, // NH농협
            "003", 0.0150  // IBK기업
    );

    private BankSpreadTable() {
    }

    /**
     * 은행 코드의 기본 스프레드 비율을 조회한다.
     *
     * @param bankCode 금융기관 표준코드. {@code null} 이거나 미등록이면 {@link #DEFAULT_BASE_SPREAD_RATIO}.
     */
    public static double baseSpreadRatio(String bankCode) {
        if (bankCode == null) {
            return DEFAULT_BASE_SPREAD_RATIO;
        }
        return BASE_SPREAD_BY_BANK.getOrDefault(bankCode, DEFAULT_BASE_SPREAD_RATIO);
    }
}
