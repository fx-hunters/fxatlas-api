package com.divurve.domain.port;

/**
 * 발급된 인증 토큰 묶음 (이슈 #9). 액세스 토큰 30분·리프레시 토큰 14일 (NFR-SE-02).
 * {@code accessTokenTtlSeconds} 는 액세스 토큰의 만료까지 남은 초로, 프론트가 갱신 타이밍 계산에 쓴다.
 */
public record AuthTokens(
        String accessToken,
        String refreshToken,
        long accessTokenTtlSeconds) {
}
