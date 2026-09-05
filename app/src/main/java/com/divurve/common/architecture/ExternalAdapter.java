package com.divurve.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * infra 패키지의 외부 연동/배치 어댑터(domain/port 구현체)임을 표시한다 (문서 4.1).
 * UseCase 에서만 호출된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExternalAdapter {
}
