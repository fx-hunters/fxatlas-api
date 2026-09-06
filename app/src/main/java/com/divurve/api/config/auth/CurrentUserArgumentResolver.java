package com.divurve.api.config.auth;

import com.divurve.common.exception.UnauthorizedException;
import com.divurve.domain.port.AuthPrincipal;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} 파라미터를 {@link CurrentUserContext} 에서 해석한다 (이슈 #50).
 *
 * <p>{@code AuthPrincipal} 전체가 필요하면 그 타입으로, 사용자 id 만 필요하면 {@code UUID} 로 받는다.
 * 컨텍스트가 비어 있으면 401 을 던진다 — 인증 강제 지점이 여기 한 곳이므로,
 * 컨트롤러가 검사를 빠뜨려 무인증으로 통과하는 경로가 존재하지 않는다.
 *
 * <p>토큰 없이 접근해야 하는 엔드포인트(로그인·회원가입·마스터 데이터·Swagger)는
 * 이 어노테이션을 쓰지 않으므로 영향을 받지 않는다. 인터셉터를 401 차단형으로 바꾸는 방식과 달리
 * 공개 경로 allowlist 를 따로 관리할 필요가 없다.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return UUID.class.equals(type) || AuthPrincipal.class.equals(type);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        AuthPrincipal principal = CurrentUserContext.get().orElseThrow(UnauthorizedException::new);
        return AuthPrincipal.class.equals(parameter.getParameterType()) ? principal : principal.userId();
    }
}
