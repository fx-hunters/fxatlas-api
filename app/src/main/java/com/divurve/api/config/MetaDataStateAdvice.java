package com.divurve.api.config;

import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.DataSourceStatus;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 응답 직전에 {@code meta.data_state} 와 {@code meta.sources} 를 채운다 (명세 v2 §1.2, 이슈 #57).
 *
 * <p>{@code ApiResponse.ok()} 는 봉투를 만들 때 {@code Meta.mock(...)} 을 하드코딩한다. 컨트롤러는
 * 외부 API 키가 설정돼 있는지 알 방법이 없고(ArchUnit 상 infra 를 볼 수 없다), 알 필요도 없다.
 * 그래서 판정은 {@link DataSourceStatus} 포트 한 곳에 두고, 봉투가 나가기 직전 여기서 한 번만 덮어쓴다 —
 * {@link MetaDemoFlagAdvice} 가 {@code is_demo} 를 채우는 것과 같은 방식이다.
 *
 * <p><b>라이브가 아니면 아무것도 바꾸지 않는다.</b> {@code Meta} 는 {@code mock} 이면 {@code sources} 를
 * 강제로 비우므로(FR-CM-10), Mock 상태에서 출처가 새어 나갈 경로가 없다.
 */
@RestControllerAdvice
public class MetaDataStateAdvice implements ResponseBodyAdvice<Object> {

    private final DataSourceStatus dataSourceStatus;

    public MetaDataStateAdvice(DataSourceStatus dataSourceStatus) {
        this.dataSourceStatus = Objects.requireNonNull(dataSourceStatus, "dataSourceStatus");
    }

    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
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
        if (body instanceof ApiResponse<?> apiResponse
                && apiResponse.meta() != null
                && dataSourceStatus.isLive()) {
            return apiResponse.withMeta(apiResponse.meta().asLive(dataSourceStatus.sources()));
        }
        return body;
    }
}
