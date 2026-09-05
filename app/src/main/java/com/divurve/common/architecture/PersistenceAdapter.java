package com.divurve.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * domain 의 Repository 커스텀 구현체(영속성 어댑터)임을 표시한다 (문서 4.1).
 * UseCase 에서만 호출된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PersistenceAdapter {
}
