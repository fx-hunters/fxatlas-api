package com.divurve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link UnauthorizedException} — 명세 §1.3 의 401 {@code UNAUTHORIZED} 고정 코드를 확인한다.
 *
 * <p>메시지를 받는 생성자는 로그인 실패(이슈 #61)처럼 "없는 계정"과 "틀린 비밀번호"를 구분해 주면 안 되는
 * 상황에서 쓴다 — 호출자가 양쪽에 동일한 문자열을 넘기는 한 사용자 열거는 이 클래스 수준에서 막힌다.
 */
class UnauthorizedExceptionTest {

    @Test
    void 기본_생성자는_인증이_필요합니다_문구의_401_UNAUTHORIZED_다() {
        UnauthorizedException e = new UnauthorizedException();

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(e.getMessage()).isEqualTo("인증이 필요합니다.");
        assertThat(e.getField()).isNull();
    }

    @Test
    void 메시지를_지정하면_그대로_실리고_field_는_비어있다() {
        UnauthorizedException e = new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(e.getMessage()).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.");
        assertThat(e.getField()).isNull();
    }

    @Test
    void ApiException_을_상속해_전역_핸들러_대상이_된다() {
        assertThat(new UnauthorizedException("m")).isInstanceOf(ApiException.class);
    }
}
