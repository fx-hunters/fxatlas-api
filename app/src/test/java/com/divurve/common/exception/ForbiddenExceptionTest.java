package com.divurve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link ForbiddenException} — 세 생성자가 에러 엔벨로프(명세 1.3)에 실릴 값을 각각 어떻게 채우는지 검증한다.
 * 상태코드는 항상 403 이며, field/detail 은 사용한 생성자에 따라서만 채워진다.
 */
class ForbiddenExceptionTest {

    @Test
    void 코드와_메시지만_주면_403_에_field_detail_은_비어있다() {
        ForbiddenException e = new ForbiddenException("SPECULATIVE_PURPOSE_BLOCKED",
                "투기성 목적의 계획은 생성할 수 없습니다");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("SPECULATIVE_PURPOSE_BLOCKED");
        assertThat(e.getMessage()).isEqualTo("투기성 목적의 계획은 생성할 수 없습니다");
        assertThat(e.getField()).isNull();
        assertThat(e.getDetail()).isNull();
    }

    @Test
    void 문자열_세번째_인자는_field_로_실린다() {
        ForbiddenException e = new ForbiddenException("SPECULATIVE_PURPOSE_BLOCKED",
                "투기성 목적의 계획은 생성할 수 없습니다", "purpose");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getField()).isEqualTo("purpose");
        assertThat(e.getDetail()).isNull();
    }

    @Test
    void 문자열이_아닌_세번째_인자는_detail_로_실린다() {
        Map<String, Object> detail = Map.of("allowed_purposes", java.util.List.of("TRAVEL", "TUITION"));

        ForbiddenException e = new ForbiddenException("SPECULATIVE_PURPOSE_BLOCKED",
                "투기성 목적의 계획은 생성할 수 없습니다", detail);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getField()).isNull();
        assertThat(e.getDetail()).isEqualTo(detail);
    }

    @Test
    void ApiException_을_상속해_전역_핸들러_대상이_된다() {
        assertThat(new ForbiddenException("C", "m")).isInstanceOf(ApiException.class);
    }
}
