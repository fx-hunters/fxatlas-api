package com.divurve.api.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 CORS 설정 — 허용 오리진을 환경변수(FRONTEND_URL)로 주입받는다.
 * 프론트 도메인을 코드에 하드코딩하지 않기 위한 설정 (0단계 배포 준비).
 *
 * <p>쉼표로 여러 오리진을 지정할 수 있다(예: 배포 프론트 + 로컬 개발 동시 허용).
 * {@code allowCredentials(true)} 는 와일드카드("*") 오리진을 허용하지 않으므로,
 * 명시적 오리진을 콤마로 나열한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] frontendUrls;

    public CorsConfig(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrls = Arrays.stream(frontendUrl.split(","))
            .map(String::trim)
            .filter(url -> !url.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(frontendUrls)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
