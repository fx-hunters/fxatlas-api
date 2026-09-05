package com.divurve.api.config.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.TokenProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link AuthTokenInterceptor} 골격 검증 — Bearer 파싱·검증·컨텍스트 주입, 그리고 요청 종료 시 정리.
 * 골격 단계이므로 어떤 경우에도 요청은 통과(preHandle=true)한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenInterceptorTest {

    @Mock
    private TokenProvider tokenProvider;

    private AuthTokenInterceptor interceptor() {
        return new AuthTokenInterceptor(tokenProvider);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void 유효한_Bearer_토큰이면_유저_컨텍스트를_주입하고_통과시킨다() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), true);
        when(tokenProvider.verify("good-token")).thenReturn(Optional.of(principal));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good-token");

        boolean proceed = interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(CurrentUserContext.get()).contains(principal);
    }

    @Test
    void 무효한_토큰이면_컨텍스트를_주입하지_않고도_통과시킨다() {
        when(tokenProvider.verify("bad-token")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");

        boolean proceed = interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void Authorization_헤더가_없으면_검증없이_통과시킨다() {
        boolean proceed = interceptor()
                .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void Bearer_접두사가_아니면_검증없이_통과시킨다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abcdef");

        boolean proceed = interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void afterCompletion_은_유저_컨텍스트를_비운다() {
        CurrentUserContext.set(new AuthPrincipal(UUID.randomUUID(), false));

        interceptor().afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(CurrentUserContext.get()).isEmpty();
    }
}
