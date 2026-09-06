package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.system.SafeModeResponse;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.system.SafeModeService;
import com.divurve.domain.system.SafeModeView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SystemController} 매핑 검증 — 도메인 뷰 → DTO 변환, data/meta 래핑, 요청 주체 해석(미인증 401).
 */
@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

    @Mock
    private SafeModeService safeModeService;

    private final UUID userId = UUID.randomUUID();

    private SystemController controller() {
        return new SystemController(safeModeService);
    }

    private void authenticate() {
        CurrentUserContext.set(new AuthPrincipal(userId, false));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getSafeMode_는_안전모드_뷰를_data_meta로_래핑한다() {
        authenticate();
        when(safeModeService.evaluateSafeMode(userId)).thenReturn(
                new SafeModeView(true, "safe_mode", List.of(
                        new SafeModeView.Check("data_staleness", false, "데이터가 오래됨"),
                        new SafeModeView.Check("volatility_high", true, null))));

        ApiResponse<SafeModeResponse> response = controller().getSafeMode();

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().active()).isTrue();
        assertThat(response.data().status()).isEqualTo("safe_mode");
        assertThat(response.data().checks()).hasSize(2);
        assertThat(response.data().checks().get(0).key()).isEqualTo("data_staleness");
        assertThat(response.data().checks().get(0).passed()).isFalse();
        assertThat(response.data().checks().get(0).reason()).isEqualTo("데이터가 오래됨");
        assertThat(response.data().checks().get(1).key()).isEqualTo("volatility_high");
        assertThat(response.data().checks().get(1).passed()).isTrue();
        assertThat(response.data().checks().get(1).reason()).isNull();
    }

    @Test
    void getSafeMode_는_점검항목이_비어도_정상_응답한다() {
        authenticate();
        when(safeModeService.evaluateSafeMode(userId))
                .thenReturn(new SafeModeView(false, "normal", List.of()));

        ApiResponse<SafeModeResponse> response = controller().getSafeMode();

        assertThat(response.data().active()).isFalse();
        assertThat(response.data().status()).isEqualTo("normal");
        assertThat(response.data().checks()).isEmpty();
    }

    @Test
    void getSafeMode_는_미인증이면_401을_던진다() {
        assertThatThrownBy(() -> controller().getSafeMode())
                .isInstanceOf(UnauthorizedException.class);
    }
}
