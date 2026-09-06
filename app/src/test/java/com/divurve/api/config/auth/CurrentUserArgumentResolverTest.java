package com.divurve.api.config.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.exception.UnauthorizedException;
import com.divurve.domain.port.AuthPrincipal;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

/**
 * {@link CurrentUserArgumentResolver} 검증 (이슈 #50).
 *
 * <p>이슈 #50 이전에는 "인증 컨텍스트가 없으면 401" 을 컨트롤러 5곳이 각자 복붙한 헬퍼로
 * 처리했고, 그 검증도 컨트롤러 테스트 5벌에 흩어져 있었다. 이제 강제 지점이 이 클래스 하나이므로
 * 검증도 여기 한 벌로 모은다 — 컨트롤러가 검사를 빠뜨려 무인증 통과하는 경로 자체가 없다.
 */
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /** 리플렉션으로 파라미터를 집어오기 위한 시그니처 표본. 호출되지 않는다. */
    @SuppressWarnings("unused")
    private void 표본(
            @CurrentUser UUID annotatedUuid,
            @CurrentUser AuthPrincipal annotatedPrincipal,
            @CurrentUser String annotatedUnsupportedType,
            UUID plainUuid) {
    }

    private MethodParameter parameter(int index) {
        Method method;
        try {
            method = getClass().getDeclaredMethod(
                    "표본", UUID.class, AuthPrincipal.class, String.class, UUID.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return new MethodParameter(method, index);
    }

    @Test
    @DisplayName("@CurrentUser 가 붙은 UUID·AuthPrincipal 파라미터만 처리한다")
    void supportsOnlyAnnotatedSupportedTypes() {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();   // @CurrentUser UUID
        assertThat(resolver.supportsParameter(parameter(1))).isTrue();   // @CurrentUser AuthPrincipal
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();  // @CurrentUser String
        assertThat(resolver.supportsParameter(parameter(3))).isFalse();  // 어노테이션 없는 UUID
    }

    @Test
    @DisplayName("UUID 파라미터에는 사용자 id 를 주입한다")
    void resolvesUserId() {
        CurrentUserContext.set(new AuthPrincipal(userId, false));

        Object resolved = resolver.resolveArgument(parameter(0), null, null, null);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    @DisplayName("AuthPrincipal 파라미터에는 주체 전체를 주입한다")
    void resolvesPrincipal() {
        AuthPrincipal principal = new AuthPrincipal(userId, true);
        CurrentUserContext.set(principal);

        Object resolved = resolver.resolveArgument(parameter(1), null, null, null);

        assertThat(resolved).isSameAs(principal);
    }

    @Test
    @DisplayName("인증 컨텍스트가 비어 있으면 401 을 던진다")
    void throwsUnauthorizedWhenContextEmpty() {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
