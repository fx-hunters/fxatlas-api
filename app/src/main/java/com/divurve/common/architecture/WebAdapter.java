package com.divurve.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * api 패키지의 컨트롤러(웹 어댑터)임을 표시한다 (문서 4.1).
 * 최상위 레이어 — 다른 레이어에서 호출받지 않는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WebAdapter {
}
