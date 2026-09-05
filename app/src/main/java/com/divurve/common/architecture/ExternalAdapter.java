package com.divurve.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * infra 패키지의 외부 연동/배치 어댑터(domain/port 구현체)임을 표시한다 (문서 4.1).
 * UseCase 에서만 호출된다.
 *
 * <p>{@link Component} 를 메타 어노테이션으로 포함하므로, 이 어노테이션이 붙은 어댑터는
 * 컴포넌트 스캔으로 Spring 빈이 되어 domain/port 인터페이스에 자동 주입된다
 * ({@link UseCase} 가 {@code @Service} 를 포함하는 것과 같은 방식).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface ExternalAdapter {
}
