package com.divurve.infra.fxrate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ECOS 오류 블록 (이슈 #57).
 *
 * <p>ECOS 는 <b>실패도 HTTP 200</b> 으로 돌려주고, 본문에 {@code StatisticSearch} 대신
 * {@code RESULT: {CODE, MESSAGE}} 를 담는다. 이 블록을 보지 않으면 인증키 오류·트래픽 초과·서버 장애가
 * 전부 "행이 없다"로 뭉개진다 — 키를 잘못 넣어도 <b>빈 결과가 정상인 것처럼</b> 흘러간다.
 * 실제로 {@code /market/regime} 은 그 빈 결과를 삼키고 200 을 반환하도록 되어 있어서,
 * 키 오류가 화면에서는 "데이터 없음"으로만 보였다.
 *
 * @param code    결과 코드 ({@code INFO-000} 정상 · {@code INFO-200} 해당 자료 없음 · 그 외 오류)
 * @param message 사람이 읽는 설명. <b>로그에만 남기고 응답에는 싣지 않는다</b>
 */
record EcosResult(
        @JsonProperty("CODE") String code,
        @JsonProperty("MESSAGE") String message) {

    /** 정상. */
    static final String CODE_OK = "INFO-000";

    /** 조건에 맞는 자료 없음 — 오류가 아니라 빈 결과다. */
    static final String CODE_NO_DATA = "INFO-200";

    /** 오류가 아닌 코드(정상·자료 없음)인지. */
    boolean isBenign() {
        return code == null || CODE_OK.equals(code) || CODE_NO_DATA.equals(code);
    }

    /** 조회 결과가 비어 있는 것이 정상인 코드인지. */
    boolean isNoData() {
        return CODE_NO_DATA.equals(code);
    }
}
