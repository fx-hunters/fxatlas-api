package com.divurve.domain.port;

import java.util.UUID;

/**
 * 검증된 요청 주체 (이슈 #9). 인증 미들웨어가 액세스 토큰을 검증해 만든 유저 컨텍스트다.
 * {@code isDemo} 는 데모 계정 여부로, 이후 보호 엔드포인트가 데모 제약(쓰기 제한 등)에 활용한다.
 */
public record AuthPrincipal(
        UUID userId,
        boolean isDemo) {
}
