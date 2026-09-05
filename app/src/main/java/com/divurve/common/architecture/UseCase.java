package com.divurve.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Service;

/**
 * domain 패키지의 도메인 서비스(유스케이스)임을 표시한다 (문서 4.1).
 * Web(컨트롤러)에서만 호출된다.
 *
 * <p>{@link Service} 를 메타 어노테이션으로 포함하므로, 이 어노테이션이 붙은 클래스는
 * 컴포넌트 스캔으로 Spring 빈이 된다 — 별도의 @Service 를 중복해 붙일 필요가 없다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service
public @interface UseCase {
}
