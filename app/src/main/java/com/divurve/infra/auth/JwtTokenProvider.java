package com.divurve.infra.auth;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;

/**
 * JWT 기반 {@link TokenProvider} 구현 (이슈 #9). HS256 서명, Spring Security 미사용.
 *
 * <p>액세스·리프레시 토큰 모두 subject 에 유저 id, {@code is_demo}·{@code token_type} 클레임을 담는다.
 * {@link #verify} 는 액세스 토큰만 통과시키고(리프레시 토큰으로는 요청을 인증하지 못한다),
 * 서명 불일치·만료·형식 오류 시 예외를 던지지 않고 빈 값을 반환한다.
 */
@ExternalAdapter
public class JwtTokenProvider implements TokenProvider {

    private static final String CLAIM_IS_DEMO = "is_demo";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenProvider(
            @Value("${app.auth.jwt-secret}") String jwtSecret,
            @Value("${app.auth.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${app.auth.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    public AuthTokens issue(UUID userId, boolean isDemo) {
        Instant now = Instant.now();
        String accessToken = buildToken(userId, isDemo, TYPE_ACCESS, now, accessTokenTtlSeconds);
        String refreshToken = buildToken(userId, isDemo, TYPE_REFRESH, now, refreshTokenTtlSeconds);
        return new AuthTokens(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    private String buildToken(UUID userId, boolean isDemo, String tokenType, Instant issuedAt, long ttlSeconds) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_IS_DEMO, isDemo)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    @Override
    public Optional<AuthPrincipal> verify(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            boolean isDemo = Boolean.TRUE.equals(claims.get(CLAIM_IS_DEMO, Boolean.class));
            return Optional.of(new AuthPrincipal(userId, isDemo));
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·만료·형식 오류·subject 파싱 실패 등은 모두 "검증 실패"로 통일한다.
            return Optional.empty();
        }
    }
}
