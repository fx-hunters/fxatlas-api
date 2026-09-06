package com.divurve.domain.route;

import com.divurve.common.exception.NotImplementedException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Route 계열 기능 플래그 (API 명세 v2 §6.2, 요구사항 v2 §4.12).
 *
 * <p><b>기본값은 {@code false} 다.</b> 요구사항 v2 §4.12 는 FR-RT-01~06 을 전부 <b>P(구조만 준비)</b> 로
 * 두고, 목적함수 · 최소 입력값 · 안전/기회 버킷 존재와 비율 · 목적별 하한선 · 권장 분할 회차 ·
 * 몬테카를로 적용 여부 · 달성 확률 정의를 <b>전부 미확정</b>으로 선언한다. 기존 문서의 50/70/85/95%
 * 비율과 4~8회 권장값은 <b>후보일 뿐 확정 요구사항이 아니다</b>. 따라서 확정 전까지 Route 계산
 * 엔드포인트는 수치를 내보내지 않고 {@code 501 NOT_IMPLEMENTED} 로 응답한다.
 *
 * <p>플래그가 막는 엔드포인트 — {@code POST /goals} · {@code PUT /goals/{id}} ·
 * {@code DELETE /goals/{id}} · {@code POST /plans/preview} · {@code POST /goals/{id}/plans} ·
 * {@code GET /goals/{id}/plans} · {@code GET /goals/{id}/plans/active} ·
 * {@code POST /plans/{id}/steps/{seq}/complete} · {@code POST /plans/{id}/steps/{seq}/skip}.
 * {@code GET /goals} 는 501 대신 빈 목록과 {@code route_enabled=false} 를 돌려준다(명세 §3·§6.2).
 *
 * <p><b>켜는 방법</b> — Route 계산 로직이 확정되어 검증까지 끝난 뒤에만 켠다. 셋 중 하나를 쓴다.
 * <ul>
 *   <li>{@code application.yml} 의 {@code route.enabled: true}</li>
 *   <li>환경변수 {@code ROUTE_ENABLED=true}</li>
 *   <li>기동 인자 {@code --route.enabled=true} (또는 {@code -Droute.enabled=true})</li>
 * </ul>
 * 테스트에서는 {@code new RouteFeatureFlag(true)} 로 켠 인스턴스를 직접 주입한다.
 *
 * <p>{@code @ConfigurationProperties} + {@code @Component} 조합이므로 세터 바인딩을 쓴다
 * (컴포넌트 스캔으로 등록되는 빈은 생성자 바인딩을 쓸 수 없다).
 */
@Component
@ConfigurationProperties(prefix = "route")
public class RouteFeatureFlag {

    private boolean enabled;

    /** 스프링 바인딩용 기본 생성자 — 기본값은 꺼짐이다. */
    public RouteFeatureFlag() {
        this(false);
    }

    /** 테스트·명시적 구성을 위한 생성자. */
    public RouteFeatureFlag(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Route 계산 경로 진입 전 호출한다. 꺼져 있으면 계산·조회를 시작하지 않고 501 로 끝낸다.
     *
     * @throws NotImplementedException 플래그가 꺼져 있을 때 (전역 핸들러가 501 + {@code NOT_IMPLEMENTED})
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new NotImplementedException();
        }
    }
}
