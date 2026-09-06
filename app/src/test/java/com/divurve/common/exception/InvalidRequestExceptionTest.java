package com.divurve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link InvalidRequestException} — 명세 §1.3 의 400 {@code VALIDATION_FAILED} 고정 코드.
 * v1 의 도메인별 상세 코드는 명세 v2 §0.1 에서 삭제됐고, 구분은 {@code field} 로만 한다.
 */
class InvalidRequestExceptionTest {

    @Test
    void 메시지만_주면_400_VALIDATION_FAILED_이고_field_는_비어있다() {
        InvalidRequestException e = new InvalidRequestException("수량은 0보다 커야 합니다.");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(e.getMessage()).isEqualTo("수량은 0보다 커야 합니다.");
        assertThat(e.getField()).isNull();
    }

    @Test
    void 문제가_된_field_를_함께_실을_수_있다() {
        InvalidRequestException e = new InvalidRequestException("수량은 0보다 커야 합니다.", "quantity");

        assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(e.getField()).isEqualTo("quantity");
    }
}
