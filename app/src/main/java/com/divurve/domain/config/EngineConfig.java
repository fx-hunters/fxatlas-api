package com.divurve.domain.config;

import com.divurve.engine.cost.EffectiveSpreadCalculator;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * engine 계산 컴포넌트를 Spring 빈으로 등록한다 (이슈 #10). engine 모듈은 프레임워크에 의존하지 않으므로
 * ({@code @EngineComponent} 는 스테레오타입이 아님) 컴포넌트 스캔 대상이 아니다 — 여기서 명시적으로 빈을 만든다.
 *
 * <p>이 설정은 domain 레이어에 둔다: engine 은 domain 에서만 접근 가능하다는 아키텍처 규칙(문서 4.3)을 지키기 위함이다.
 * engine 계산 서비스는 상태가 없는 순수 함수이므로 싱글턴 빈으로 안전하게 공유된다.
 */
@Configuration
public class EngineConfig {

    @Bean
    public RiskProfileScorer riskProfileScorer() {
        return new RiskProfileScorer();
    }

    @Bean
    public EffectiveSpreadCalculator effectiveSpreadCalculator() {
        return new EffectiveSpreadCalculator();
    }
}
