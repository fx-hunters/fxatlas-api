package com.fxatlas.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * domain 패키지의 도메인 서비스(유스케이스)임을 표시한다 (문서 4.1).
 * Web(컨트롤러)에서만 호출된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UseCase {
}
