package com.divurve.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.exception.NotImplementedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RouteFeatureFlag} — 기본값(꺼짐)과 게이트 동작 검증.
 *
 * <p>기본값이 꺼짐이라는 사실 자체가 요구사항이다 — Route 계산은 요구사항 v2 §4.12 에서
 * 전부 미확정이므로, 설정을 빠뜨린 환경에서 확정되지 않은 수치가 새어 나가면 안 된다.
 */
@DisplayName("RouteFeatureFlag")
class RouteFeatureFlagTest {

    @Test
    @DisplayName("기본 생성자의 기본값은 꺼짐이다")
    void defaultsToDisabled() {
        assertThat(new RouteFeatureFlag().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("꺼져 있으면 requireEnabled 가 501(NotImplementedException) 을 던진다")
    void requireEnabledThrowsWhenDisabled() {
        assertThatThrownBy(() -> new RouteFeatureFlag(false).requireEnabled())
                .isInstanceOf(NotImplementedException.class);
    }

    @Test
    @DisplayName("켜져 있으면 requireEnabled 가 통과한다")
    void requireEnabledPassesWhenEnabled() {
        assertThatCode(() -> new RouteFeatureFlag(true).requireEnabled()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("세터 바인딩으로 켤 수 있다 — application.yml / ROUTE_ENABLED 가 쓰는 경로")
    void setterBinding() {
        RouteFeatureFlag flag = new RouteFeatureFlag();
        flag.setEnabled(true);

        assertThat(flag.isEnabled()).isTrue();
        assertThatCode(flag::requireEnabled).doesNotThrowAnyException();
    }
}
