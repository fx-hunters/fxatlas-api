package com.divurve.api.config.auth;

import com.divurve.domain.port.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증 미들웨어 골격 (이슈 #9). {@code Authorization: Bearer <token>} 헤더를 파싱하고,
 * 액세스 토큰을 {@link TokenProvider} 로 검증한 뒤 {@link CurrentUserContext} 에 유저 컨텍스트를 주입한다.
 *
 * <p>골격 단계이므로 토큰이 없거나 무효해도 요청을 통과시킨다(현재 모든 엔드포인트는 무인증).
 * 실제 차단은 이후 보호 엔드포인트 도입 시 이 지점을 확장해 처리한다.
 */
public class AuthTokenInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public AuthTokenInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        resolveToken(request)
                .flatMap(tokenProvider::verify)
                .ifPresent(CurrentUserContext::set);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
