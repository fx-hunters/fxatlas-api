package com.fxatlas.domain.port;

/**
 * 외부 환율 조회 포트 (문서 3.1).
 * domain 은 이 인터페이스만 알며, 실제 구현(infra 의 EcosFxRateProvider 등)은 알지 못한다(DIP).
 * 런타임에는 Spring 이 infra 의 유일한 구현체를 자동 주입한다.
 */
public interface FxRateProvider {

    RateSnapshot fetchLatest(String pairCode);
}
