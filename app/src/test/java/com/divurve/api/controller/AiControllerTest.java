package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.ai.ExplainRequest;
import com.divurve.api.dto.ai.ExplainResponse;
import com.divurve.api.dto.ai.ParseGoalRequest;
import com.divurve.api.dto.ai.ParseGoalResponse;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.ai.AiService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AiController} 매핑 및 입력 검증 테스트.
 * explain/parseGoal 응답이 올바르게 data/meta 래핑되는지 확인.
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Test
    void parseGoal_는_자연어를_구조화된_목표로_변환한다() {
        when(aiService.parseGoal("월 1000달러 목표 저축"))
                .thenReturn(new AiService.ParsedGoal(
                        "wealth",
                        "retirement",
                        "USD",
                        1000.0,
                        "monthly",
                        Map.of("kind", 0.95, "purpose", 0.90),
                        List.of()));

        AiController controller = new AiController(aiService);
        ParseGoalRequest request = new ParseGoalRequest("월 1000달러 목표 저축");

        ApiResponse<ParseGoalResponse> response = controller.parseGoal(request);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data()).isNotNull();
        ParseGoalResponse body = response.data();
        assertThat(body.parsed().kind()).isEqualTo("wealth");
        assertThat(body.parsed().currencyCode()).isEqualTo("USD");
        assertThat(body.confidence()).containsEntry("kind", 0.95);
    }

    @Test
    void parseGoal_text가_null이면_InvalidRequestException을_던진다() {
        AiController controller = new AiController(aiService);
        ParseGoalRequest request = new ParseGoalRequest(null);

        assertThatThrownBy(() -> controller.parseGoal(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("text");
    }

    @Test
    void parseGoal_text가_blank이면_InvalidRequestException을_던진다() {
        AiController controller = new AiController(aiService);
        ParseGoalRequest request = new ParseGoalRequest("   ");

        assertThatThrownBy(() -> controller.parseGoal(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("text");
    }

    @Test
    void explain_는_엔진_결과를_서술로_변환한다() {
        when(aiService.explain("concise", Map.of("total", 100000.0)))
                .thenReturn("귀사의 자산은 100000입니다.");

        AiController controller = new AiController(aiService);
        ExplainRequest request = new ExplainRequest("concise", Map.of("total", 100000.0));

        ApiResponse<ExplainResponse> response = controller.explain(request);

        assertThat(response.meta()).isNotNull();
        ExplainResponse body = response.data();
        assertThat(body.narrative()).contains("100000");
    }

    @Test
    void explain_수치_불일치로_인해_null이_반환되면_InvalidRequestException을_던진다() {
        when(aiService.explain("concise", Map.of("total", 100000.0)))
                .thenReturn(null);

        AiController controller = new AiController(aiService);
        ExplainRequest request = new ExplainRequest("concise", Map.of("total", 100000.0));

        assertThatThrownBy(() -> controller.explain(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최대 재시도 횟수");
    }

    @Test
    void explain_profile이_null이면_InvalidRequestException을_던진다() {
        AiController controller = new AiController(aiService);
        ExplainRequest request = new ExplainRequest(null, Map.of("total", 100000.0));

        assertThatThrownBy(() -> controller.explain(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("profile");
    }

    @Test
    void explain_metrics가_empty이면_InvalidRequestException을_던진다() {
        AiController controller = new AiController(aiService);
        ExplainRequest request = new ExplainRequest("concise", Map.of());

        assertThatThrownBy(() -> controller.explain(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("metrics");
    }
}
