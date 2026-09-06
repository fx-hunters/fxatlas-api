package com.divurve.engine.safemode;

import java.util.List;

/**
 * 안전모드 평가 결과 (명세 3.9, FR-SF-01~05).
 * 6가지 발동조건 각각에 대해 통과 여부를 기록한다.
 *
 * @param active 안전모드 발동 여부 (하나 이상의 조건 위반 시 true)
 * @param status 상태 라벨 (normal / caution / safe_mode)
 * @param checks 개별 조건 평가 결과 리스트
 */
public record SafeModeCheckResult(
    boolean active,
    String status,
    List<Check> checks) {

    /**
     * 개별 안전모드 점검 항목.
     *
     * @param key    조건 식별자 (예: data_staleness, volatility_high)
     * @param passed true면 정상(조건 미충족), false면 위반(조건 충족, 안전모드 발동 사유)
     * @param reason 위반 시 사유 (생략 가능)
     */
    public record Check(String key, boolean passed, String reason) {
    }
}
