package com.divurve.domain.system;

import java.util.List;

/**
 * 안전모드 평가 조회 결과 (명세 3.9, FR-SF-01~05).
 * engine 의 계산 결과를 도메인→웹 전달용 값으로 평탄화한 경계 객체다.
 * api 레이어가 engine 타입을 직접 참조하지 않도록(패키지 의존 방향: Engine ← Domain ← Api) 이 뷰를 거친다.
 *
 * @param active 안전모드 발동 여부 (하나 이상의 조건 위반 시 true)
 * @param status 상태 라벨 (normal / caution / safe_mode)
 * @param checks 개별 조건 평가 결과
 */
public record SafeModeView(boolean active, String status, List<Check> checks) {

    /**
     * 개별 안전모드 점검 항목.
     *
     * @param key    조건 식별자 (예: data_staleness, volatility_high)
     * @param passed true면 정상(조건 미충족), false면 위반(안전모드 발동 사유)
     * @param reason 위반 시 사유 (생략 가능)
     */
    public record Check(String key, boolean passed, String reason) {
    }
}
