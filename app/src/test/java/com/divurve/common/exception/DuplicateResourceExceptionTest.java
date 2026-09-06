package com.divurve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link DuplicateResourceException} — 명세 §1.3 의 409 {@code DUPLICATE_RESOURCE}.
 */
class DuplicateResourceExceptionTest {

    @Test
    void 메시지만_주면_409_DUPLICATE_RESOURCE_이고_field_는_비어있다() {
        DuplicateResourceException e = new DuplicateResourceException("이미 등록된 외화 예금입니다");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(e.getMessage()).isEqualTo("이미 등록된 외화 예금입니다");
        assertThat(e.getField()).isNull();
    }

    @Test
    void 중복을_일으킨_field_를_함께_실을_수_있다() {
        DuplicateResourceException e =
                new DuplicateResourceException("이미 등록된 외화 예금입니다", "bank_code");

        assertThat(e.getField()).isEqualTo("bank_code");
    }

    @Test
    void ApiException_을_상속해_전역_핸들러_대상이_된다() {
        assertThat(new DuplicateResourceException("m")).isInstanceOf(ApiException.class);
    }
}
