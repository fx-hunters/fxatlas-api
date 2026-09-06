package com.divurve.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link Meta} — API 명세 v2 §1.2 응답 메타 규약을 고정한다.
 * 직렬화 키(snake_case)와 생략 규칙까지 검증해, 프론트가 보는 실제 JSON 을 계약으로 잠근다.
 */
class MetaTest {

    private static final Instant AS_OF = Instant.parse("2026-09-01T15:30:00Z");

    /** {@code application.yml} 의 Jackson 설정(SNAKE_CASE + non_null)을 그대로 재현한 매퍼. */
    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    void mock_은_data_state가_mock이고_sources는_비어있다() {
        Meta meta = Meta.mock(AS_OF);

        assertThat(meta.asOf()).isEqualTo(AS_OF);
        assertThat(meta.dataState()).isEqualTo("mock");
        assertThat(meta.sources()).isEmpty();
        assertThat(meta.isDemo()).isFalse();
        assertThat(meta.regime()).isNull();
        assertThat(meta.modelVersion()).isNull();
    }

    @Test
    void live_는_data_state가_live이고_출처를_담는다() {
        Meta meta = Meta.live(AS_OF, List.of("ECOS", "FRED"));

        assertThat(meta.dataState()).isEqualTo("live");
        assertThat(meta.sources()).containsExactly("ECOS", "FRED");
    }

    @Test
    void live_는_출처가_null이면_빈_목록으로_담는다() {
        assertThat(Meta.live(AS_OF, null).sources()).isEmpty();
    }

    @Test
    void live_의_출처는_방어적으로_복사된다() {
        List<String> mutable = new ArrayList<>(List.of("ECOS"));

        Meta meta = Meta.live(AS_OF, mutable);
        mutable.add("MADE_UP");

        assertThat(meta.sources()).containsExactly("ECOS");
        assertThatThrownBy(() -> meta.sources().add("MADE_UP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mock_이면_출처를_넘겨도_비운다_FR_CM_10() {
        Meta meta = new Meta(AS_OF, Meta.MOCK, List.of("만들어낸출처"), false, null, null);

        assertThat(meta.sources()).isEmpty();
    }

    @Test
    void withDemo_는_데모여부만_바꾼_새_메타를_만든다() {
        Meta base = Meta.live(AS_OF, List.of("ECOS"));

        Meta demo = base.withDemo(true);

        assertThat(demo.isDemo()).isTrue();
        assertThat(base.isDemo()).isFalse();
        assertThat(demo.asOf()).isEqualTo(AS_OF);
        assertThat(demo.dataState()).isEqualTo("live");
        assertThat(demo.sources()).containsExactly("ECOS");
    }

    @Test
    void withRegime_은_시장국면만_붙인다() {
        Meta meta = Meta.mock(AS_OF).withRegime("elevated");

        assertThat(meta.regime()).isEqualTo("elevated");
        assertThat(meta.modelVersion()).isNull();
        assertThat(meta.dataState()).isEqualTo("mock");
    }

    @Test
    void withModelVersion_은_모델버전만_붙인다() {
        Meta meta = Meta.mock(AS_OF).withRegime("elevated").withModelVersion("fc-2026.08.3");

        assertThat(meta.modelVersion()).isEqualTo("fc-2026.08.3");
        assertThat(meta.regime()).isEqualTo("elevated");
    }

    @Test
    void 직렬화_키는_명세_1_2_의_snake_case_이름을_쓴다() throws Exception {
        String json = objectMapper().writeValueAsString(
                Meta.mock(AS_OF).withDemo(true).withRegime("elevated"));

        assertThat(json).isEqualTo("{\"as_of\":\"2026-09-01T15:30:00Z\",\"data_state\":\"mock\","
                + "\"sources\":[],\"is_demo\":true,\"regime\":\"elevated\"}");
    }

    @Test
    void regime_과_model_version_은_값이_없으면_생략된다() throws Exception {
        String json = objectMapper().writeValueAsString(Meta.mock(AS_OF));

        assertThat(json).doesNotContain("regime").doesNotContain("model_version");
        // is_demo 는 원시 boolean 이라 false 여도 항상 실린다(명세 §1.1 "모든 응답의 meta.is_demo").
        assertThat(json).contains("\"is_demo\":false");
    }

    @Test
    void forecast_계열_메타는_model_version_까지_싣는다() throws Exception {
        String json = objectMapper().writeValueAsString(
                Meta.mock(AS_OF).withRegime("elevated").withModelVersion("fc-2026.08.3"));

        assertThat(json).contains("\"model_version\":\"fc-2026.08.3\"");
    }
}
