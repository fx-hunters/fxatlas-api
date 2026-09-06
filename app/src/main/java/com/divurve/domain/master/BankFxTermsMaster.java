package com.divurve.domain.master;

import com.divurve.domain.settings.BankSpreadTable;
import java.util.List;
import java.util.Map;

/**
 * 은행별 환전 조건 마스터 (이슈 #11, FR-RT-11 선행 데이터). 통화·채널별 고시 스프레드({@code list_spread})와
 * 건당 고정수수료({@code fixed_fee_krw})를 은행 코드로 조회한다. 이 값들은 M2 플래너 비용계산의 입력이다.
 *
 * <p>스프레드의 은행 축은 {@link BankSpreadTable}(USD 현찰 기준 대표 스프레드)을 재사용하고, 통화·채널 축은
 * 아래 표본 배수/고정수수료 표로 조립한다. 요청 시점에는 <b>표 조회·조립</b>만 하며 비용계산(우대율 적용 등)은
 * 하지 않는다 — 그것은 engine 이 담당한다.
 */
public final class BankFxTermsMaster {

    /** 환전 조건을 제공하는 통화(표시 순서 보존). */
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "JPY");

    /** 통화별 스프레드 배수 (은행 USD 현찰 기준 스프레드에 곱한다). */
    private static final Map<String, Double> CURRENCY_MULTIPLIER = Map.of(
            "USD", 1.0,
            "EUR", 1.15,
            "JPY", 1.05);

    /** 채널별 (스프레드 배수, 건당 고정수수료 KRW). */
    private static final List<Channel> CHANNELS = List.of(
            new Channel("cash", 1.0, 0L),      // 현찰
            new Channel("transfer", 0.55, 10_000L)); // 전신환(송금)

    private BankFxTermsMaster() {
    }

    /** 마스터에 등록된 은행 코드인지 여부. */
    public static boolean contains(String bankCode) {
        return bankCode != null && BankSpreadTable.isRegistered(bankCode);
    }

    /**
     * 은행의 통화·채널별 환전 조건 전체를 반환한다. 미등록 은행은 빈 목록을 반환한다
     * (등록 여부 판단은 {@link #contains(String)} 로 하고, 404 매핑은 상위 유스케이스가 담당).
     */
    public static List<Term> termsOf(String bankCode) {
        if (!contains(bankCode)) {
            return List.of();
        }
        double baseSpread = BankSpreadTable.baseSpreadRatio(bankCode);
        return CURRENCIES.stream()
                .flatMap(currency -> CHANNELS.stream().map(channel -> new Term(
                        currency,
                        channel.name(),
                        baseSpread * CURRENCY_MULTIPLIER.get(currency) * channel.spreadMultiplier(),
                        channel.fixedFeeKrw())))
                .toList();
    }

    private record Channel(String name, double spreadMultiplier, long fixedFeeKrw) {
    }

    /**
     * 통화·채널별 환전 조건.
     *
     * @param currencyCode ISO 4217 통화 코드
     * @param channel      환전 채널 (cash·transfer)
     * @param listSpread   고시 스프레드 비율
     * @param fixedFeeKrw  건당 고정수수료(원)
     */
    public record Term(String currencyCode, String channel, double listSpread, long fixedFeeKrw) {
    }
}
