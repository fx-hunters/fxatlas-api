package com.divurve.domain.route;

import static java.util.Objects.requireNonNull;

import com.divurve.common.architecture.UseCase;
import java.time.Clock;
import java.time.Instant;

/**
 * RouteContext 직렬화 서비스 (API 명세 v2 §6.1, FR-RT-01) — <b>P(구조만 준비)</b>.
 *
 * <p><b>계산은 하지 않는다.</b> 요구사항 v2 §4.12 가 Route 의 목적함수 · 버킷 비율 · 분할 회차 ·
 * 달성 확률 정의를 전부 미확정으로 두었으므로, 이 서비스는 수집한 값을 <b>모아 전달하는 계약</b>만
 * 담당한다. 지금 단계에서는 기준 시각을 제외한 모든 값이 비어 있다.
 *
 * <p>값을 채우는 것은 후속 단계다 — X-Ray(자산) · Forecast(기준 환율) · RiskProfile(진단) ·
 * Stress(스트레스) 서비스의 공개 계약이 확정되면 그때 주입해 {@link RouteContext} 를 채운다.
 * 이번 단계에서 그 서비스들을 참조하지 않는 이유는 계약(필드 구조)을 먼저 고정하기 위해서다.
 *
 * <p>🔒 {@code model_path} · {@code forecast_factors} 는 계약에서 제외되어 있다 (FR-FC-12).
 * 자세한 근거는 {@link RouteContext} 클래스 주석 참고.
 */
@UseCase
public class RouteContextService {

    private final Clock clock;

    public RouteContextService(Clock clock) {
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * 현재 RouteContext 를 만든다. 기준 시각만 실제 값이고 나머지는 비어 있다.
     *
     * @return 값이 비어 있는 RouteContext
     */
    public RouteContext getContext() {
        return RouteContext.empty(Instant.now(clock));
    }
}
