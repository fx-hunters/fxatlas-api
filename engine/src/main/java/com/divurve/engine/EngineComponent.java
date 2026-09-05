package com.divurve.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * engine 모듈의 계산 서비스임을 표시한다 (문서 4.1).
 * UseCase(도메인 서비스)에서만 호출될 수 있다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EngineComponent {
}
