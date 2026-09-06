package com.divurve.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void of_페이로드만_주면_현재_데이터상태인_mock_메타가_채워진다() {
        Instant before = Instant.now();

        ApiResponse<String> response = ApiResponse.of("hello");

        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.meta().asOf()).isBetween(before, Instant.now());
        assertThat(response.meta().dataState()).isEqualTo("mock");
        assertThat(response.meta().sources()).isEmpty();
        assertThat(response.meta().isDemo()).isFalse();
    }

    @Test
    void of_메타를_직접_지정할_수_있다() {
        Meta meta = Meta.live(Instant.parse("2026-01-01T00:00:00Z"), List.of("ECOS"));

        ApiResponse<Integer> response = ApiResponse.of(42, meta);

        assertThat(response.data()).isEqualTo(42);
        assertThat(response.meta()).isSameAs(meta);
    }

    @Test
    void withMeta_는_페이로드를_유지한_채_메타만_교체한다() {
        ApiResponse<String> original = ApiResponse.of("hello");

        ApiResponse<String> updated = original.withMeta(original.meta().withDemo(true));

        assertThat(updated.data()).isEqualTo("hello");
        assertThat(updated.meta().isDemo()).isTrue();
        assertThat(original.meta().isDemo()).isFalse();
    }
}
