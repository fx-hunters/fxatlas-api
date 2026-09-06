package com.divurve.api.config;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.common.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 응답 직전에 {@code meta.is_demo} 를 채운다 — 명세 §1.1 "둘러보기는 <b>모든 응답</b>의
 * {@code meta.is_demo} 가 {@code true} 가 된다"(FR-IS-09).
 *
 * <p>컨트롤러마다 데모 여부를 조회해 메타에 싣게 하면 누락이 생기고, ArchUnit 규칙(이슈 #50)상
 * 컨트롤러는 {@link CurrentUserContext} 를 직접 읽을 수도 없다. 그래서 봉투를 만드는 쪽
 * ({@code ApiResponse.of})은 기본값 {@code false} 로 두고, 요청 주체를 아는 이 지점에서 한 번만 덮어쓴다.
 */
@RestControllerAdvice
public class MetaDemoFlagAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse && apiResponse.meta() != null) {
            return apiResponse.withMeta(apiResponse.meta().withDemo(CurrentUserContext.isDemo()));
        }
        return body;
    }
}
