package com.divurve.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void of_페이로드만_주면_메타가_자동으로_채워진다() {
        ApiResponse<String> response = ApiResponse.of("hello");

        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().timestamp()).isNotNull();
    }

    @Test
    void of_메타를_직접_지정할_수_있다() {
        Meta meta = new Meta(Instant.parse("2026-01-01T00:00:00Z"));

        ApiResponse<Integer> response = ApiResponse.of(42, meta);

        assertThat(response.data()).isEqualTo(42);
        assertThat(response.meta()).isSameAs(meta);
        assertThat(response.meta().timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void meta_now_는_현재_시각을_담는다() {
        Instant before = Instant.now();

        Meta meta = Meta.now();

        assertThat(meta.timestamp()).isBetween(before, Instant.now());
    }
}
