package com.divurve.api.config;

import com.divurve.api.config.auth.CurrentUser;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 생성 설정 (문서 6장).
 *
 * <p>{@link CurrentUser} 는 커스텀 {@code HandlerMethodArgumentResolver} 가 인증 컨텍스트에서
 * 채우는 파라미터다. 이를 알려주지 않으면 springdoc 이 <b>모든 보호 엔드포인트에 {@code userId}
 * 쿼리 파라미터가 있는 것처럼</b> 문서를 만든다 — 이슈 #50 에서 없애려는 바로 그 형태를
 * 문서가 다시 프론트에 권하는 셈이 된다. 그래서 문서 생성에서 제외한다.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }
}
