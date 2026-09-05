package com.divurve.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 인증 토큰 발급·검증 포트 (이슈 #9). domain 은 이 인터페이스만 알고, 구현(JWT 등)은 infra 가 제공한다(DIP).
 *
 * <p>이 계약은 데모 세션 발급(이슈 #9)과 이후 signup/login/refresh 본구현(M3-14)이 함께 재사용한다.
 */
public interface TokenProvider {

    /** 유저 식별자와 데모 여부로 액세스·리프레시 토큰을 발급한다. 토큰 payload 에 {@code is_demo} 가 실린다. */
    AuthTokens issue(UUID userId, boolean isDemo);

    /**
     * 액세스 토큰을 검증해 요청 주체를 복원한다.
     * 서명 불일치·만료·형식 오류·액세스 토큰이 아닌 경우 등 검증 실패 시 빈 값을 반환한다(예외를 던지지 않는다).
     */
    Optional<AuthPrincipal> verify(String accessToken);
}
