package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.api.dto.notifications.NotificationsResponse;
import com.divurve.common.response.ApiResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link NotificationController} 매핑 검증.
 *
 * <p>"인증 컨텍스트가 없으면 401" 은 이제 컨트롤러가 아니라
 * {@code CurrentUserArgumentResolver} 의 책임이다 (이슈 #50) — 해당 검증은
 * {@code CurrentUserArgumentResolverTest} 에 한 벌로 모여 있다.
 * 컨트롤러가 {@code @CurrentUser} 파라미터를 받는 이상, 미인증 요청은 여기까지 도달하지 못한다.
 */
class NotificationControllerTest {

    private final UUID userId = UUID.randomUUID();

    private NotificationController controller() {
        return new NotificationController();
    }

    @Test
    void getNotifications_은_알림목록을_래핑한다() {
        ApiResponse<NotificationsResponse> response = controller().getNotifications(userId);

        assertThat(response.data().notifications()).isEmpty();
    }

    @Test
    void getNotifications_은_응답을_ApiResponse로_래핑한다() {
        ApiResponse<NotificationsResponse> response = controller().getNotifications(userId);

        assertThat(response.data()).isNotNull();
        assertThat(response.meta()).isNotNull();
    }
}
