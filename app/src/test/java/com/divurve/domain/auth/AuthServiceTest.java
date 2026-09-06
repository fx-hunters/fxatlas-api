package com.divurve.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
        User savedUser = User.create(email, name, "hashed", purpose);
        when(userRepository.save(any(User.class))).thenReturn(User.create(email, name, "hashed", purpose));

        AuthTokens expectedTokens = new AuthTokens("access", "refresh", 1800);
        when(tokenProvider.issue(any(UUID.class), false)).thenReturn(expectedTokens);

        AuthTokens result = authService.signup(email, password, name, purpose);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(userRepository).findByEmail(email);
        verify(userRepository).save(any(User.class));
        verify(tokenProvider).issue(any(UUID.class), false);
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
        when(userRepository.findByEmail("email@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.signup("email@example.com", "password", null, "PURPOSE"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    void signup_purposeNull() {
        when(userRepository.findByEmail("email@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.signup("email@example.com", "password", "name", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("onboardingPurpose must not be null");
    }

    @Test
    void login_success() {
        String email = "user@example.com";
        String password = "password123";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String passwordHash = encoder.encode(password);

        User user = User.create(email, "User Name", passwordHash, "OVERSEAS_INVESTMENT");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthTokens expectedTokens = new AuthTokens("access", "refresh", 1800);
        when(tokenProvider.issue(user.getId(), false)).thenReturn(expectedTokens);

        AuthTokens result = authService.login(email, password);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(userRepository).findByEmail(email);
        verify(tokenProvider).issue(user.getId(), false);
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
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String passwordHash = encoder.encode(password);

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
        when(userRepository.findByEmail("email@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login("email@example.com", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password must not be null");
    }

    @Test
    void refreshAccessToken_success() {
        String refreshToken = "refresh_token";
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, false);

        when(tokenProvider.verifyRefreshToken(refreshToken)).thenReturn(Optional.of(principal));
        AuthTokens newTokens = new AuthTokens("new_access", "refresh", 1800);
        when(tokenProvider.issue(userId, false)).thenReturn(newTokens);

        AuthTokens result = authService.refreshAccessToken(refreshToken);

        assertThat(result.accessToken()).isEqualTo("new_access");
        assertThat(result.refreshToken()).isEqualTo(refreshToken);
        assertThat(result.accessTokenTtlSeconds()).isEqualTo(1800);
        verify(tokenProvider).verifyRefreshToken(refreshToken);
        verify(tokenProvider).issue(userId, false);
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
}
