package com.divurve.api.config.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 현재 요청 주체를 주입한다 (이슈 #50).
 * {@code UUID}(사용자 id) 또는 {@link com.divurve.domain.port.AuthPrincipal} 타입에 붙일 수 있다.
 *
 * <pre>{@code
 * @GetMapping("/goals")
 * public ApiResponse<GoalListResponse> listGoals(@CurrentUser UUID userId) { ... }
 * }</pre>
 *
 * <p>인증 컨텍스트가 비어 있으면 {@link CurrentUserArgumentResolver} 가
 * {@code UnauthorizedException}(401) 을 던지므로, 이 어노테이션이 붙은 엔드포인트는
 * <b>토큰 없이 도달할 수 없다</b>. 이슈 #50 이전에는 각 컨트롤러가 {@code currentUserId()}
 * 헬퍼를 복붙해 호출해야 했고, 호출을 빠뜨리면 조용히 무인증으로 통과했다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}
