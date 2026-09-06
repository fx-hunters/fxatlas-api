package com.divurve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link ForbiddenException} — 명세 §1.3 의 403 {@code FORBIDDEN} 고정 코드를 확인한다.
 * 코드는 호출자가 지정하지 못하며(6종 닫힌 집합), 구분이 필요하면 {@code field} 만 채운다.
 */
class ForbiddenExceptionTest {

    @Test
    void 메시지만_주면_403_FORBIDDEN_이고_field_는_비어있다() {
        ForbiddenException e = new ForbiddenException("타인의 리소스에 접근할 수 없습니다");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("FORBIDDEN");
        assertThat(e.getMessage()).isEqualTo("타인의 리소스에 접근할 수 없습니다");
        assertThat(e.getField()).isNull();
    }

    @Test
    void field_를_함께_실을_수_있다() {
        ForbiddenException e = new ForbiddenException("타인의 리소스에 접근할 수 없습니다", "goal_id");

        assertThat(e.getCode()).isEqualTo("FORBIDDEN");
        assertThat(e.getField()).isEqualTo("goal_id");
    }

    @Test
    void ApiException_을_상속해_전역_핸들러_대상이_된다() {
        assertThat(new ForbiddenException("m")).isInstanceOf(ApiException.class);
    }
}
