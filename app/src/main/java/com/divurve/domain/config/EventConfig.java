package com.divurve.domain.config;

import com.divurve.domain.event.EconEventValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 경제 이벤트 도메인의 프레임워크 무의존 컴포넌트를 Spring 빈으로 등록한다 (이슈 #74).
 *
 * <p>{@link EconEventValidator} 는 {@link EngineConfig} 가 등록하는 engine 계산기들과 같은 성격이다 —
 * 상태 없는 결정론적 순수 로직이라 스테레오타입 어노테이션을 붙이지 않았고, 따라서 컴포넌트 스캔
 * 대상이 아니다. 여기서 명시적으로 빈을 만들지 않으면 {@code EconEventIngestionService} 주입이 실패해
 * 컨텍스트가 기동하지 않는다.
 *
 * <p>{@code EngineConfig} 에 합치지 않은 이유는 그 클래스가 <b>engine 모듈</b> 컴포넌트 전용이기 때문이다.
 * 검증기는 engine 이 아니라 domain 에 속한다 — 수치를 만들지 않고 추출 결과를 걸러낼 뿐이다.
 */
@Configuration
public class EventConfig {

    @Bean
    public EconEventValidator econEventValidator() {
        return new EconEventValidator();
    }
}
