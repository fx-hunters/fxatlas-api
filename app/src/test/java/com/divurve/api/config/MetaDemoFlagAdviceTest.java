package com.divurve.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.common.response.ApiResponse;
import com.divurve.common.response.Meta;
import com.divurve.domain.port.AuthPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MetaDemoFlagAdvice} — 명세 §1.1 "둘러보기는 모든 응답의 {@code meta.is_demo} 가 true".
 * 컨트롤러가 만든 봉투를 응답 직전에 한 번만 덮어쓰는지, 봉투가 아닌 본문은 손대지 않는지 확인한다.
 */
class MetaDemoFlagAdviceTest {

    private final MetaDemoFlagAdvice advice = new MetaDemoFlagAdvice();

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void 모든_컨버터에_적용된다() {
        assertThat(advice.supports(null, null)).isTrue();
    }

    @Test
    void 데모_계정이면_is_demo_가_true_로_채워진다() {
        CurrentUserContext.set(new AuthPrincipal(UUID.randomUUID(), true));

        Object result = beforeBodyWrite(ApiResponse.of("payload"));

        assertThat(result).isInstanceOfSatisfying(ApiResponse.class, response -> {
            assertThat(response.data()).isEqualTo("payload");
            assertThat(((Meta) response.meta()).isDemo()).isTrue();
        });
    }

    @Test
    void 일반_계정이면_is_demo_가_false_로_남는다() {
        CurrentUserContext.set(new AuthPrincipal(UUID.randomUUID(), false));

        Object result = beforeBodyWrite(ApiResponse.of("payload"));

        assertThat(((ApiResponse<?>) result).meta().isDemo()).isFalse();
    }

    @Test
    void 미인증_요청이면_is_demo_가_false_다() {
        Object result = beforeBodyWrite(ApiResponse.of("payload"));

        assertThat(((ApiResponse<?>) result).meta().isDemo()).isFalse();
    }

    @Test
    void 나머지_메타_필드는_그대로_유지된다() {
        CurrentUserContext.set(new AuthPrincipal(UUID.randomUUID(), true));
        Instant asOf = Instant.parse("2026-09-01T15:30:00Z");
        Meta meta = Meta.live(asOf, java.util.List.of("ECOS")).withRegime("elevated");

        ApiResponse<?> result = (ApiResponse<?>) beforeBodyWrite(ApiResponse.of("payload", meta));

        assertThat(result.meta().asOf()).isEqualTo(asOf);
        assertThat(result.meta().dataState()).isEqualTo("live");
        assertThat(result.meta().sources()).containsExactly("ECOS");
        assertThat(result.meta().regime()).isEqualTo("elevated");
        assertThat(result.meta().isDemo()).isTrue();
    }

    @Test
    void 봉투가_아닌_응답은_그대로_통과시킨다() {
        CurrentUserContext.set(new AuthPrincipal(UUID.randomUUID(), true));
        String raw = "swagger json";

        assertThat(beforeBodyWrite(raw)).isSameAs(raw);
    }

    @Test
    void 메타가_없는_봉투는_그대로_통과시킨다() {
        ApiResponse<String> noMeta = new ApiResponse<>("payload", null);

        assertThat(beforeBodyWrite(noMeta)).isSameAs(noMeta);
    }

    private Object beforeBodyWrite(Object body) {
        return advice.beforeBodyWrite(body, null, null, null, null, null);
    }
}
