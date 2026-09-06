package com.divurve.domain.port;

/**
 * 외부 거시지표(FRED 등) 조회 포트.
 * domain 은 이 인터페이스만 알며, 구현체(infra 의 FredMacroProvider 등)는 알지 못한다 (DIP).
 */
public interface MacroIndicatorProvider {

    /**
     * 지정한 시리즈의 최신 일별 관측값을 반환한다. 실시간 시세를 조회하지 않는다(일별 종가/관측 기준).
     */
    MacroSnapshot fetchLatest(String seriesId);
}
