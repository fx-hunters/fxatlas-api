package com.divurve.api.config.auth;

import com.divurve.domain.port.AuthPrincipal;
import java.util.Optional;

/**
 * 요청 스코프 유저 컨텍스트 홀더 (이슈 #9 — 유저 컨텍스트 주입 골격).
 * 인증 미들웨어가 검증한 {@link AuthPrincipal} 을 ThreadLocal 에 담고, 요청 종료 시 반드시 비운다.
 *
 * <p>이후 보호 엔드포인트/유스케이스가 현재 요청 주체를 조회하는 단일 진입점이다.
 * 토큰이 없거나 무효한 요청에서는 빈 값이 조회된다(골격 단계에서는 차단하지 않는다).
 */
public final class CurrentUserContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    /** 검증된 요청 주체를 현재 스레드에 설정한다. */
    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    /** 현재 요청 주체를 조회한다. 미인증 요청이면 빈 값. */
    public static Optional<AuthPrincipal> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /** ThreadLocal 을 비운다. 스레드 풀 재사용 시 컨텍스트 누수를 막기 위해 요청 종료마다 호출한다. */
    public static void clear() {
        HOLDER.remove();
    }
}
