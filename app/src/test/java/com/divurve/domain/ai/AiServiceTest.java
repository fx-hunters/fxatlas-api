package com.divurve.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.port.AiProvider;
import com.divurve.domain.port.AiProvider.ExplainContext;
import com.divurve.domain.port.AiProvider.ExplainResult;
import com.divurve.domain.settings.SettingsView;
import com.divurve.domain.settings.UserSettingsService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AiService} 유스케이스 테스트 (API 명세 v2 §5.12).
 * 성공 시 그대로 반환, 검증 실패 시 폴백(H1 대응 — 400 이 아니라 200 + fallback:true), 사용자 설정에서
 * explain_level·explain_domain 을 읽는지(M2 대응)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiProvider aiProvider;

    @Mock
    private AiResponseValidator validator;

    @Mock
    private NarrativeFilter narrativeFilter;

    @Mock
    private UserSettingsService userSettingsService;

    private final UUID userId = UUID.randomUUID();
    private final Map<String, Object> facts = Map.of("amount", 100000.0);

    private AiService service;

    @BeforeEach
    void setUp() {
        service = new AiService(aiProvider, validator, narrativeFilter, userSettingsService);
    }

    private void stubSettings(String level, String domain) {
        when(userSettingsService.getSettings(userId)).thenReturn(new SettingsView(
                null, 0.0, level, domain, 0.0, 0.0, true, true, true, false, true));
    }

    @Test
    void explain_수치와_표현이_모두_통과하면_그대로_반환한다() {
        stubSettings("standard", "finance");
        List<String> sentences = List.of("자산은 100000입니다.");
        when(aiProvider.explain(any(ExplainContext.class))).thenReturn(new ExplainResult(sentences));
        when(validator.verify(sentences, facts)).thenReturn(true);
        when(narrativeFilter.detect("자산은 100000입니다.")).thenReturn(List.of());

        AiService.ExplainOutcome outcome = service.explain(userId, "profile_fit", facts);

        assertThat(outcome.sentences()).isEqualTo(sentences);
        assertThat(outcome.fallback()).isFalse();
        assertThat(outcome.numericMatch()).isTrue();
        assertThat(outcome.blockedPhrases()).isEmpty();
        assertThat(outcome.explainLevel()).isEqualTo("standard");
        assertThat(outcome.explainDomain()).isEqualTo("finance");
    }

    @Test
    void explain_수치_불일치가_재시도_후에도_계속되면_폴백을_반환한다() {
        stubSettings("simple", "plain");
        List<String> bad = List.of("자산은 999999입니다.");
        when(aiProvider.explain(any(ExplainContext.class))).thenReturn(new ExplainResult(bad));
        when(validator.verify(bad, facts)).thenReturn(false);
        when(narrativeFilter.detect("자산은 999999입니다.")).thenReturn(List.of());

        AiService.ExplainOutcome outcome = service.explain(userId, "profile_fit", facts);

        assertThat(outcome.fallback()).isTrue();
        assertThat(outcome.sentences()).isEqualTo(AiService.FALLBACK_SENTENCES);
        // H1 — 실패해도 numericMatch 는 true 로 보고한다(폴백 문장은 수치를 담지 않는다).
        assertThat(outcome.numericMatch()).isTrue();
        assertThat(outcome.blockedPhrases()).isEmpty();
        verify(aiProvider, times(AiService.MAX_ATTEMPTS)).explain(any(ExplainContext.class));
    }

    @Test
    void explain_금지_표현이_발견되면_폴백을_반환한다() {
        stubSettings("simple", "plain");
        List<String> risky = List.of("반드시 매수하세요.");
        when(aiProvider.explain(any(ExplainContext.class))).thenReturn(new ExplainResult(risky));
        when(validator.verify(risky, facts)).thenReturn(true);
        when(narrativeFilter.detect("반드시 매수하세요.")).thenReturn(List.of("반드시", "매수하세요"));

        AiService.ExplainOutcome outcome = service.explain(userId, "profile_fit", facts);

        assertThat(outcome.fallback()).isTrue();
        assertThat(outcome.sentences()).isEqualTo(AiService.FALLBACK_SENTENCES);
    }

    @Test
    void explain_사용자_설정의_explainLevel_explainDomain을_provider에게_전달한다() {
        stubSettings("detailed", "dev");
        List<String> sentences = List.of("변동성 지표는 5년 백분위 72%에 해당합니다.");
        when(aiProvider.explain(any(ExplainContext.class))).thenReturn(new ExplainResult(sentences));
        when(validator.verify(sentences, facts)).thenReturn(true);
        when(narrativeFilter.detect(sentences.get(0))).thenReturn(List.of());

        service.explain(userId, "forecast_summary", facts);

        verify(aiProvider).explain(new ExplainContext("forecast_summary", facts, "detailed", "dev"));
    }

    @Test
    void explain_userId가_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> service.explain(null, "profile_fit", facts))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void explain_surface가_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> service.explain(userId, null, facts))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void explain_facts가_null이면_NullPointerException을_던진다() {
        assertThatThrownBy(() -> service.explain(userId, "profile_fit", null))
                .isInstanceOf(NullPointerException.class);
    }
}
