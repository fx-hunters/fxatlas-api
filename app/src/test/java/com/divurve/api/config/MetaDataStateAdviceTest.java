package com.divurve.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.response.ApiResponse;
import com.divurve.common.response.Meta;
import com.divurve.domain.port.DataSourceStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MetaDataStateAdvice} — {@code meta.data_state} 가 실제 연결 상태를 말하는지 (이슈 #57).
 *
 * <p>이전에는 {@code ApiResponse.ok()} 가 {@code mock} 을 하드코딩해서, 실제 ECOS 수치를 내보내면서도
 * 응답은 "시연용"이라고 말했다.
 */
@DisplayName("MetaDataStateAdvice")
class MetaDataStateAdviceTest {

    private static final Instant AS_OF = Instant.parse("2026-09-01T15:30:00Z");

    /** 라이브 여부만 다른 최소 스텁 — 포트 계약이 두 메서드뿐이라 모킹 프레임워크가 필요 없다. */
    private record StubStatus(boolean live, List<String> sources) implements DataSourceStatus {
        @Override
        public boolean isLive() {
            return live;
        }
    }

    private static MetaDataStateAdvice advice(boolean live, String... sources) {
        return new MetaDataStateAdvice(new StubStatus(live, List.of(sources)));
    }

    private static Object write(MetaDataStateAdvice advice, Object body) {
        return advice.beforeBodyWrite(body, null, null, null, null, null);
    }

    @Test
    @DisplayName("모든 컨버터에 적용된다")
    void 모든_컨버터에_적용된다() {
        assertThat(advice(true, "ECOS").supports(null, null)).isTrue();
    }

    @Test
    @DisplayName("라이브면 data_state 가 live 로 바뀌고 출처가 채워진다")
    void 라이브면_live_와_출처를_채운다() {
        ApiResponse<?> result = (ApiResponse<?>)
                write(advice(true, "ECOS"), ApiResponse.of("payload", Meta.mock(AS_OF)));

        assertThat(result.meta().dataState()).isEqualTo(Meta.LIVE);
        assertThat(result.meta().sources()).containsExactly("ECOS");
        assertThat(result.data()).isEqualTo("payload");
    }

    @Test
    @DisplayName("라이브가 아니면 mock 그대로 두고 출처도 비운다 (FR-CM-10)")
    void 라이브가_아니면_mock_이다() {
        ApiResponse<?> result = (ApiResponse<?>)
                write(advice(false), ApiResponse.of("payload", Meta.mock(AS_OF)));

        assertThat(result.meta().dataState()).isEqualTo(Meta.MOCK);
        assertThat(result.meta().sources()).isEmpty();
    }

    @Test
    @DisplayName("나머지 메타 필드는 그대로 유지된다")
    void 나머지_메타는_유지된다() {
        Meta meta = Meta.mock(AS_OF).withRegime("elevated").withModelVersion("fc-1").withDemo(true);

        ApiResponse<?> result = (ApiResponse<?>)
                write(advice(true, "ECOS"), ApiResponse.of("payload", meta));

        assertThat(result.meta().asOf()).isEqualTo(AS_OF);
        assertThat(result.meta().regime()).isEqualTo("elevated");
        assertThat(result.meta().modelVersion()).isEqualTo("fc-1");
        assertThat(result.meta().isDemo()).isTrue();
    }

    @Test
    @DisplayName("메타가 없는 봉투는 손대지 않는다")
    void 메타가_없으면_그대로_둔다() {
        ApiResponse<String> body = ApiResponse.of("payload", null);

        assertThat(write(advice(true, "ECOS"), body)).isSameAs(body);
    }

    @Test
    @DisplayName("봉투가 아닌 응답은 그대로 통과시킨다 (예: Swagger JSON)")
    void 봉투가_아니면_그대로_통과시킨다() {
        String raw = "swagger json";

        assertThat(write(advice(true, "ECOS"), raw)).isSameAs(raw);
    }

    @Test
    @DisplayName("포트는 필수다")
    void 포트는_필수다() {
        assertThatThrownBy(() -> new MetaDataStateAdvice(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataSourceStatus");
    }
}
