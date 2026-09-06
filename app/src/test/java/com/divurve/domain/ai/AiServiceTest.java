package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.port.AiProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AiService} 유스케이스 테스트.
 * explain: 수치 대조 + 필터링 + 재시도 로직
 * parseGoal: confidence 반환 검증
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiProvider aiProvider;

    @Mock
    private AiResponseValidator validator;

    @Mock
    private NarrativeFilter narrativeFilter;

    @Test
    void explain_수치가_일치하면_필터링된_서술을_반환한다() {
        Map<String, Object> metrics = Map.of("amount", 100000.0);
        String originalNarrative = "귀사의 자산은 100000입니다.";
        String filteredNarrative = "귀사의 자산은 100000입니다.";

        when(aiProvider.explain("concise", metrics))
                .thenReturn(new AiProvider.ExplainResult(originalNarrative));
        when(validator.validateNarrative(originalNarrative, metrics))
                .thenReturn(true);
        when(narrativeFilter.filter(originalNarrative))
                .thenReturn(filteredNarrative);

        AiService service = new AiService(aiProvider, validator, narrativeFilter);
        String result = service.explain("concise", metrics);

        assertThat(result).isEqualTo(filteredNarrative);
    }

    @Test
    void explain_수치_불일치시_재시도한다() {
        Map<String, Object> metrics = Map.of("amount", 100000.0);
        String validNarrative = "귀사의 자산은 100000입니다.";

        when(aiProvider.explain("concise", metrics))
                .thenReturn(new AiProvider.ExplainResult("잘못된 수치 999999입니다."))
                .thenReturn(new AiProvider.ExplainResult("또 잘못된 수치 888888입니다."))
                .thenReturn(new AiProvider.ExplainResult(validNarrative));
        when(validator.validateNarrative("잘못된 수치 999999입니다.", metrics))
                .thenReturn(false);
        when(validator.validateNarrative("또 잘못된 수치 888888입니다.", metrics))
                .thenReturn(false);
        when(validator.validateNarrative(validNarrative, metrics))
                .thenReturn(true);
        when(narrativeFilter.filter(validNarrative))
                .thenReturn(validNarrative);

        AiService service = new AiService(aiProvider, validator, narrativeFilter);
        String result = service.explain("concise", metrics);

        assertThat(result).isEqualTo(validNarrative);
        verify(aiProvider, times(3)).explain("concise", metrics);
    }

    @Test
    void explain_최대_재시도_초과시_null을_반환한다() {
        Map<String, Object> metrics = Map.of("amount", 100000.0);

        when(aiProvider.explain("concise", metrics))
                .thenReturn(new AiProvider.ExplainResult("잘못된 수치입니다."));
        when(validator.validateNarrative(anyString(), anyMap()))
                .thenReturn(false);

        AiService service = new AiService(aiProvider, validator, narrativeFilter);
        String result = service.explain("concise", metrics);

        assertThat(result).isNull();
        verify(aiProvider, times(3)).explain("concise", metrics);
    }

    @Test
    void explain_profile이_null이면_NullPointerException을_던진다() {
        AiService service = new AiService(aiProvider, validator, narrativeFilter);

        assertThatThrownBy(() -> service.explain(null, Map.of("amount", 100.0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void explain_metrics가_null이면_NullPointerException을_던진다() {
        AiService service = new AiService(aiProvider, validator, narrativeFilter);

        assertThatThrownBy(() -> service.explain("concise", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseGoal_자연어를_구조화하고_confidence를_반환한다() {
        Map<String, Double> confidence = Map.of(
                "kind", 0.95,
                "purpose", 0.90,
                "currencyCode", 0.98);
        List<String> missing = List.of();

        when(aiProvider.parseGoal("월 1000달러 저축"))
                .thenReturn(new AiProvider.ParseResult(
                        "wealth",
                        "retirement",
                        "USD",
                        1000.0,
                        "monthly",
                        confidence,
                        missing));

        AiService service = new AiService(aiProvider, validator, narrativeFilter);
        AiService.ParsedGoal result = service.parseGoal("월 1000달러 저축");

        assertThat(result.kind()).isEqualTo("wealth");
        assertThat(result.purpose()).isEqualTo("retirement");
        assertThat(result.currencyCode()).isEqualTo("USD");
        assertThat(result.targetAmount()).isEqualTo(1000.0);
        assertThat(result.recurInterval()).isEqualTo("monthly");
        assertThat(result.confidence()).containsEntry("kind", 0.95);
        assertThat(result.missing()).isEmpty();
    }

    @Test
    void parseGoal_text가_null이면_NullPointerException을_던진다() {
        AiService service = new AiService(aiProvider, validator, narrativeFilter);

        assertThatThrownBy(() -> service.parseGoal(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseGoal_낮은_confidence는_클라이언트에_전달되어_재확인을_권고한다() {
        Map<String, Double> confidence = Map.of(
                "kind", 0.95,
                "purpose", 0.45,  // 낮은 신뢰도
                "currencyCode", 0.98);

        when(aiProvider.parseGoal("목표 저축 (불명확)"))
                .thenReturn(new AiProvider.ParseResult(
                        "wealth",
                        "education",
                        "USD",
                        null,
                        "monthly",
                        confidence,
                        List.of("targetAmount")));

        AiService service = new AiService(aiProvider, validator, narrativeFilter);
        AiService.ParsedGoal result = service.parseGoal("목표 저축 (불명확)");

        // 낮은 confidence 도 그대로 전달되어, 클라이언트가 사용자 확인을 받을 수 있게 함
        assertThat(result.confidence().get("purpose")).isLessThan(0.5);
        assertThat(result.missing()).contains("targetAmount");
    }
}
