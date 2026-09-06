package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.auth.LoginRequest;
import com.divurve.api.dto.auth.RefreshRequest;
import com.divurve.api.dto.auth.SignupRequest;
import com.divurve.api.dto.auth.TokenResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.auth.AuthDemoService;
import com.divurve.domain.auth.AuthService;
import com.divurve.domain.port.AuthTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthController} 매핑 검증 — signup/login/refresh/demo 엔드포인트.
 * AuthTokens → TokenResponse(data/meta 래핑, is_demo 적절히 설정).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthDemoService authDemoService;

    @Test
    void signup_는_발급된_토큰을_is_demo_false로_래핑해_반환한다() {
        SignupRequest request = new SignupRequest(
                "user@example.com",
                "password123",
                "User Name",
                "OVERSEAS_INVESTMENT");

        when(authService.signup("user@example.com", "password123", "User Name", "OVERSEAS_INVESTMENT"))
                .thenReturn(new AuthTokens("access-token", "refresh-token", 1800L));
        AuthController controller = new AuthController(authService, authDemoService);

        ApiResponse<TokenResponse> response = controller.signup(request);

        assertThat(response.meta()).isNotNull();
        TokenResponse body = response.data();
        assertThat(body.accessToken()).isEqualTo("access-token");
        assertThat(body.refreshToken()).isEqualTo("refresh-token");
        assertThat(body.expiresIn()).isEqualTo(1800L);
        assertThat(body.isDemo()).isFalse();
    }

    @Test
    void login_는_발급된_토큰을_is_demo_false로_래핑해_반환한다() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");

        when(authService.login("user@example.com", "password123"))
                .thenReturn(new AuthTokens("access-token", "refresh-token", 1800L));
        AuthController controller = new AuthController(authService, authDemoService);

        ApiResponse<TokenResponse> response = controller.login(request);

        assertThat(response.meta()).isNotNull();
        TokenResponse body = response.data();
        assertThat(body.accessToken()).isEqualTo("access-token");
        assertThat(body.refreshToken()).isEqualTo("refresh-token");
        assertThat(body.expiresIn()).isEqualTo(1800L);
        assertThat(body.isDemo()).isFalse();
    }

    @Test
    void refresh_는_발급된_토큰을_is_demo_false로_래핑해_반환한다() {
        RefreshRequest request = new RefreshRequest("refresh-token");

        when(authService.refreshAccessToken("refresh-token"))
                .thenReturn(new AuthTokens("new-access-token", "refresh-token", 1800L));
        AuthController controller = new AuthController(authService, authDemoService);

        ApiResponse<TokenResponse> response = controller.refresh(request);

        assertThat(response.meta()).isNotNull();
        TokenResponse body = response.data();
        assertThat(body.accessToken()).isEqualTo("new-access-token");
        assertThat(body.refreshToken()).isEqualTo("refresh-token");
        assertThat(body.expiresIn()).isEqualTo(1800L);
        assertThat(body.isDemo()).isFalse();
    }

    @Test
    void demo_는_발급된_토큰을_is_demo_true로_래핑해_반환한다() {
        when(authDemoService.createDemoSession())
                .thenReturn(new AuthTokens("access-token", "refresh-token", 1800L));
        AuthController controller = new AuthController(authService, authDemoService);

        ApiResponse<TokenResponse> response = controller.demo();

        assertThat(response.meta()).isNotNull();
        TokenResponse body = response.data();
        assertThat(body.accessToken()).isEqualTo("access-token");
        assertThat(body.refreshToken()).isEqualTo("refresh-token");
        assertThat(body.expiresIn()).isEqualTo(1800L);
        assertThat(body.isDemo()).isTrue();
    }
}
