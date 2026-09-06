package com.divurve.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.auth.AuthService.AuthResult;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * {@link AuthService} 단위 테스트 — 가입·로그인·토큰 갱신.
 *
 * <p>로그인·갱신 결과에는 {@code onboarded}(초기 설정 완료 여부)가 함께 실린다 —
 * 클라이언트는 이 값 하나로 초기 설정으로 보낼지 정한다(FR-IS-01·FR-IS-07).
 */
class AuthServiceTest {

    private AuthService authService;
    private UserRepository userRepository;
    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenProvider = mock(TokenProvider.class);
        authService = new AuthService(userRepository, tokenProvider);
    }

    @Test
    void signup_success() {
        String email = "user@example.com";
        String password = "password123";
        String name = "User Name";
        String purpose = "OVERSEAS_INVESTMENT";
        UUID userId = UUID.randomUUID();

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        // save() 가 돌려주는 것은 JPA 가 id 를 채운 엔티티다. 단위테스트에서는 리플렉션으로 주입한다.
        User savedUser = User.create(email, name, "hashed", purpose);
        assignId(savedUser, userId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthTokens expectedTokens = new AuthTokens("access", "refresh", 1800);
        when(tokenProvider.issue(userId, false)).thenReturn(expectedTokens);

        AuthTokens result = authService.signup(email, password, name, purpose);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(userRepository).findByEmail(email);
        verify(userRepository).save(any(User.class));
        verify(tokenProvider).issue(userId, false);
    }

    @Test
    void signup_emailAlreadyExists() {
        String email = "user@example.com";
        String password = "password123";
        String name = "User Name";
        String purpose = "OVERSEAS_INVESTMENT";

        User existingUser = User.create(email, name, "hashed", purpose);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.signup(email, password, name, purpose))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void signup_emailNull() {
        assertThatThrownBy(() -> authService.signup(null, "password", "name", "PURPOSE"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("email must not be null");
    }

    @Test
    void signup_passwordNull() {
        assertThatThrownBy(() -> authService.signup("email@example.com", null, "name", "PURPOSE"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password must not be null");
    }

    @Test
    void signup_nameNull() {
        assertThatThrownBy(() -> authService.signup("email@example.com", "password", null, "PURPOSE"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    void signup_purposeNull() {
        assertThatThrownBy(() -> authService.signup("email@example.com", "password", "name", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("onboardingPurpose must not be null");
    }

    @Test
    void login_success() {
        String email = "user@example.com";
        String password = "password123";
        String passwordHash = new BCryptPasswordEncoder().encode(password);

        User user = User.create(email, "User Name", passwordHash, "OVERSEAS_INVESTMENT");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tokenProvider.issue(user.getId(), false)).thenReturn(new AuthTokens("access", "refresh", 1800));

        AuthResult result = authService.login(email, password);

        assertThat(result.tokens().accessToken()).isEqualTo("access");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh");
        // 초기 설정을 마치지 않은 계정 — 클라이언트는 초기 설정으로 보낸다.
        assertThat(result.onboarded()).isFalse();
        verify(userRepository).findByEmail(email);
        verify(tokenProvider).issue(user.getId(), false);
    }

    @Test
    void login_초기설정을_마친_사용자는_onboarded_true() {
        String email = "done@example.com";
        String password = "password123";
        String passwordHash = new BCryptPasswordEncoder().encode(password);

        User user = User.create(email, "User Name", passwordHash, "OVERSEAS_INVESTMENT");
        user.completeOnboarding(Instant.parse("2026-09-01T15:30:00Z"));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tokenProvider.issue(user.getId(), false)).thenReturn(new AuthTokens("access", "refresh", 1800));

        assertThat(authService.login(email, password).onboarded()).isTrue();
    }

    @Test
    void login_userNotFound() {
        String email = "user@example.com";
        String password = "password123";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(email, password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void login_invalidPassword() {
        String email = "user@example.com";
        String password = "password123";
        String wrongPassword = "wrongpassword";
        String passwordHash = new BCryptPasswordEncoder().encode(password);

        User user = User.create(email, "User Name", passwordHash, "OVERSEAS_INVESTMENT");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(email, wrongPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid password");
    }

    @Test
    void login_emailNull() {
        assertThatThrownBy(() -> authService.login(null, "password"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("email must not be null");
    }

    @Test
    void login_passwordNull() {
        assertThatThrownBy(() -> authService.login("email@example.com", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password must not be null");
    }

    @Test
    void refreshAccessToken_success() {
        String refreshToken = "refresh_token";
        UUID userId = UUID.randomUUID();

        when(tokenProvider.verifyRefreshToken(refreshToken))
                .thenReturn(Optional.of(new AuthPrincipal(userId, false)));
        when(tokenProvider.issue(userId, false)).thenReturn(new AuthTokens("new_access", "refresh", 1800));

        User user = User.create("user@example.com", "User Name", "hashed", "OVERSEAS_INVESTMENT");
        user.completeOnboarding(Instant.parse("2026-09-01T15:30:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthResult result = authService.refreshAccessToken(refreshToken);

        assertThat(result.tokens().accessToken()).isEqualTo("new_access");
        assertThat(result.tokens().refreshToken()).isEqualTo(refreshToken);
        assertThat(result.tokens().accessTokenTtlSeconds()).isEqualTo(1800);
        assertThat(result.onboarded()).isTrue();
        verify(tokenProvider).verifyRefreshToken(refreshToken);
        verify(tokenProvider).issue(userId, false);
    }

    /** 토큰은 유효한데 사용자 행이 사라진 경우 — 갱신은 되지만 초기 설정 미완료로 본다. */
    @Test
    void refreshAccessToken_사용자를_찾지_못하면_onboarded_false() {
        String refreshToken = "refresh_token";
        UUID userId = UUID.randomUUID();

        when(tokenProvider.verifyRefreshToken(refreshToken))
                .thenReturn(Optional.of(new AuthPrincipal(userId, false)));
        when(tokenProvider.issue(userId, false)).thenReturn(new AuthTokens("new_access", "refresh", 1800));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(authService.refreshAccessToken(refreshToken).onboarded()).isFalse();
    }

    @Test
    void refreshAccessToken_invalidToken() {
        String refreshToken = "invalid_token";

        when(tokenProvider.verifyRefreshToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshAccessToken_tokenNull() {
        assertThatThrownBy(() -> authService.refreshAccessToken(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("refreshToken must not be null");
    }

    @Test
    void passwordHashingIsNotPlaintext() {
        String password = "password123";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String passwordHash = encoder.encode(password);

        assertThat(passwordHash).isNotEqualTo(password);
        assertThat(passwordHash).startsWith("$2");
        assertThat(encoder.matches(password, passwordHash)).isTrue();
    }

    /** 단위테스트에서 JPA 가 채우는 UUID 를 리플렉션으로 주입한다. */
    private static void assignId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
