package com.divurve.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 배선 (이슈 #74). 프로젝트에 스케줄링을 켜는 곳이 아직 없었다.
 *
 * <p>{@code app.external.anthropic.extract-schedule-enabled=true} 일 때만 {@code @EnableScheduling}
 * 을 활성화한다 — 기본은 꺼짐. {@link AnthropicConfig} 와 같은 이유다: 배치가 기본으로 돌아
 * 의도치 않게 외부 API 를 호출하거나(추출 경로) 앞으로 추가될 다른 스케줄 작업을 조용히 깨우면
 * 안 된다. 이 클래스를 프로퍼티 뒤에 둠으로써, 스케줄링 자체를 켜고 끄는 지점을 하나로 모은다.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "app.external.anthropic", name = "extract-schedule-enabled", havingValue = "true")
@EnableScheduling
public class SchedulingConfig {
}
