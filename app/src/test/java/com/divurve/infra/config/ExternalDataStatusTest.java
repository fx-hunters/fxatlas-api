package com.divurve.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.infra.fxrate.EcosProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ExternalDataStatus} — {@code meta.data_state} 판정 (이슈 #57).
 */
@DisplayName("ExternalDataStatus")
class ExternalDataStatusTest {

    private static ExternalDataStatus withApiKey(String apiKey) {
        return new ExternalDataStatus(new EcosProperties(
                "https://ecos.bok.or.kr/api", apiKey, "731Y001", Map.of("USD_KRW", "0000001")));
    }

    @Test
    @DisplayName("ECOS 키가 있으면 라이브다")
    void 키가_있으면_라이브다() {
        assertThat(withApiKey("REAL_KEY").isLive()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("키가 비어 있으면 라이브가 아니다 — 외부 호출이 전부 실패하는 상태다")
    void 키가_없으면_라이브가_아니다(String apiKey) {
        assertThat(withApiKey(apiKey).isLive()).isFalse();
    }

    @Test
    @DisplayName("라이브면 ECOS 를 출처로 밝힌다")
    void 라이브면_ECOS를_출처로_밝힌다() {
        assertThat(withApiKey("REAL_KEY").sources())
                .containsExactly(ExternalDataStatus.SOURCE_ECOS);
    }

    @Test
    @DisplayName("라이브가 아니면 출처를 만들어내지 않는다 (FR-CM-10)")
    void 라이브가_아니면_출처가_비어_있다() {
        assertThat(withApiKey("").sources()).isEmpty();
    }

    @Test
    @DisplayName("출처 목록은 수정할 수 없다")
    void 출처_목록은_불변이다() {
        assertThatThrownBy(() -> withApiKey("REAL_KEY").sources().add("FRED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("설정은 필수다")
    void 설정은_필수다() {
        assertThatThrownBy(() -> new ExternalDataStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ecosProperties");
    }
}
