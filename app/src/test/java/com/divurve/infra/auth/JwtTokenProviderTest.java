package com.divurve.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.AuthTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtTokenProvider} 단위 테스트 — 발급·검증의 모든 분기(정상·타입 불일치·서명 위조·만료·형식 오류)를 덮는다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-0123456789abcdef";
    private static final long ACCESS_TTL = 1800L;
    private static final long REFRESH_TTL = 1_209_600L;

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, ACCESS_TTL, REFRESH_TTL);

    @Test
    void issue_는_access와_refresh_토큰과_access_ttl을_반환한다() {
        UUID userId = UUID.randomUUID();

        AuthTokens tokens = provider.issue(userId, true);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(tokens.accessTokenTtlSeconds()).isEqualTo(ACCESS_TTL);
    }

    @Test
    void verify_는_발급한_access_토큰에서_유저_컨텍스트를_복원한다_데모() {
        UUID userId = UUID.randomUUID();
        AuthTokens tokens = provider.issue(userId, true);

        Optional<AuthPrincipal> principal = provider.verify(tokens.accessToken());

        assertThat(principal).isPresent().get().satisfies(p -> {
            assertThat(p.userId()).isEqualTo(userId);
            assertThat(p.isDemo()).isTrue();
        });
    }

    @Test
    void verify_는_is_demo가_false인_토큰도_복원한다() {
        UUID userId = UUID.randomUUID();
        AuthTokens tokens = provider.issue(userId, false);

        assertThat(provider.verify(tokens.accessToken()))
                .isPresent().get()
                .satisfies(p -> assertThat(p.isDemo()).isFalse());
    }

    @Test
    void verify_는_refresh_토큰을_거부한다() {
        AuthTokens tokens = provider.issue(UUID.randomUUID(), true);

        // 리프레시 토큰(token_type=refresh)으로는 요청을 인증하지 못한다.
        assertThat(provider.verify(tokens.refreshToken())).isEmpty();
    }

    @Test
    void verify_는_다른_키로_서명된_토큰을_거부한다() {
        AuthTokens forged = new JwtTokenProvider(
                "another-secret-key-totally-different-9876543210zyxwv", ACCESS_TTL, REFRESH_TTL)
                .issue(UUID.randomUUID(), true);

        assertThat(provider.verify(forged.accessToken())).isEmpty();
    }

    @Test
    void verify_는_형식이_깨진_토큰을_거부한다() {
        assertThat(provider.verify("not-a-jwt")).isEmpty();
    }

    @Test
    void verify_는_만료된_토큰을_거부한다() {
        // TTL 을 음수로 줘 이미 만료된 액세스 토큰을 만든다.
        AuthTokens expired = new JwtTokenProvider(SECRET, -10L, REFRESH_TTL)
                .issue(UUID.randomUUID(), true);

        assertThat(provider.verify(expired.accessToken())).isEmpty();
    }

    @Test
    void verify_는_subject가_UUID가_아니면_거부한다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String badSubject = Jwts.builder()
                .subject("not-a-uuid")
                .claim("is_demo", true)
                .claim("token_type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(provider.verify(badSubject)).isEmpty();
    }

    @Test
    void verifyRefreshToken_는_발급한_refresh_토큰에서_유저_컨텍스트를_복원한다() {
        UUID userId = UUID.randomUUID();
        AuthTokens tokens = provider.issue(userId, true);

        Optional<AuthPrincipal> principal = provider.verifyRefreshToken(tokens.refreshToken());

        assertThat(principal).isPresent().get().satisfies(p -> {
            assertThat(p.userId()).isEqualTo(userId);
            assertThat(p.isDemo()).isTrue();
        });
    }

    @Test
    void verifyRefreshToken_는_refresh_토큰의_is_demo가_false인_경우도_복원한다() {
        UUID userId = UUID.randomUUID();
        AuthTokens tokens = provider.issue(userId, false);

        assertThat(provider.verifyRefreshToken(tokens.refreshToken()))
                .isPresent().get()
                .satisfies(p -> assertThat(p.isDemo()).isFalse());
    }

    @Test
    void verifyRefreshToken_는_access_토큰을_거부한다() {
        AuthTokens tokens = provider.issue(UUID.randomUUID(), true);

        // 액세스 토큰(token_type=access)으로는 리프레시가 작동하지 않는다.
        assertThat(provider.verifyRefreshToken(tokens.accessToken())).isEmpty();
    }

    @Test
    void verifyRefreshToken_는_다른_키로_서명된_토큰을_거부한다() {
        AuthTokens forged = new JwtTokenProvider(
                "another-secret-key-totally-different-9876543210zyxwv", ACCESS_TTL, REFRESH_TTL)
                .issue(UUID.randomUUID(), true);

        assertThat(provider.verifyRefreshToken(forged.refreshToken())).isEmpty();
    }

    @Test
    void verifyRefreshToken_는_형식이_깨진_토큰을_거부한다() {
        assertThat(provider.verifyRefreshToken("not-a-jwt")).isEmpty();
    }

    @Test
    void verifyRefreshToken_는_만료된_토큰을_거부한다() {
        // TTL 을 음수로 줘 이미 만료된 리프레시 토큰을 만든다.
        AuthTokens expired = new JwtTokenProvider(SECRET, ACCESS_TTL, -10L)
                .issue(UUID.randomUUID(), true);

        assertThat(provider.verifyRefreshToken(expired.refreshToken())).isEmpty();
    }
}
