package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.notifications.NotificationsResponse;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link NotificationController} 매핑 검증.
 */
class NotificationControllerTest {

    private final UUID userId = UUID.randomUUID();

    private NotificationController controller() {
        return new NotificationController();
    }

    private void authenticate() {
        CurrentUserContext.set(new AuthPrincipal(userId, false));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getNotifications_은_알림목록을_래핑한다() {
        authenticate();

        ApiResponse<NotificationsResponse> response = controller().getNotifications();

        assertThat(response.data().notifications()).isEmpty();
    }

    @Test
    void 인증_컨텍스트가_없으면_401() {
        assertThatThrownBy(() -> controller().getNotifications())
                .isInstanceOf(UnauthorizedException.class);
    }
}
