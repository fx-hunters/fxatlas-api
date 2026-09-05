package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.auth.TokenResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.auth.AuthDemoService;
import com.divurve.domain.port.AuthTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthController#demo()} 매핑 검증 — AuthTokens → TokenResponse(data/meta 래핑, is_demo=true).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthDemoService authDemoService;

    @Test
    void demo_는_발급된_토큰을_is_demo_true로_래핑해_반환한다() {
        when(authDemoService.createDemoSession())
                .thenReturn(new AuthTokens("access-token", "refresh-token", 1800L));
        AuthController controller = new AuthController(authDemoService);

        ApiResponse<TokenResponse> response = controller.demo();

        assertThat(response.meta()).isNotNull();
        TokenResponse body = response.data();
        assertThat(body.accessToken()).isEqualTo("access-token");
        assertThat(body.refreshToken()).isEqualTo("refresh-token");
        assertThat(body.expiresIn()).isEqualTo(1800L);
        assertThat(body.isDemo()).isTrue();
    }
}
