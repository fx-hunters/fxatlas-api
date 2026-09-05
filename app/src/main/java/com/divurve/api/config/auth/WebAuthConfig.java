package com.divurve.api.config.auth;

import com.divurve.domain.port.TokenProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증 미들웨어 등록 (이슈 #9). {@link AuthTokenInterceptor} 를 {@code /api/**} 경로에 건다.
 * 검증 로직 자체는 {@link TokenProvider}(infra 구현) 에 위임하므로 여기서는 배선만 담당한다.
 */
@Configuration
public class WebAuthConfig implements WebMvcConfigurer {

    private final TokenProvider tokenProvider;

    public WebAuthConfig(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthTokenInterceptor(tokenProvider))
                .addPathPatterns("/api/**");
    }
}
