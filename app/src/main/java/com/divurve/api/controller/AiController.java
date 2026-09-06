package com.divurve.api.controller;

import com.divurve.api.dto.ai.ExplainRequest;
import com.divurve.api.dto.ai.ExplainResponse;
import com.divurve.api.dto.ai.ParseGoalRequest;
import com.divurve.api.dto.ai.ParseGoalResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.ai.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 엔드포인트 (명세 4장, 이슈 #23).
 * NFR-AI-01: AI 는 산술을 하지 않고 엔진 결과만 인용한다.
 * NFR-AI-02: 수치 불일치 시 폐기·재생성한다.
 * NFR-AI-03: 단정적·권유 표현은 후처리로 차단한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "자연어 목표 구조화·엔진 결과 서술")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = Objects.requireNonNull(aiService);
    }

    @Operation(summary = "자연어 문장을 목표 제약으로 구조화")
    @PostMapping("/parse-goal")
    public ApiResponse<ParseGoalResponse> parseGoal(@RequestBody ParseGoalRequest request) {
        validateParseGoalRequest(request);

        AiService.ParsedGoal parsed = aiService.parseGoal(request.text());

        ParseGoalResponse response = new ParseGoalResponse(
                new ParseGoalResponse.Parsed(
                        parsed.kind(),
                        parsed.purpose(),
                        parsed.currencyCode(),
                        parsed.targetAmount(),
                        parsed.recurInterval()),
                parsed.confidence(),
                parsed.missing());

        return ApiResponse.of(response);
    }

    @Operation(summary = "엔진 결과를 설명 프로필에 맞춰 서술")
    @PostMapping("/explain")
    public ApiResponse<ExplainResponse> explain(@RequestBody ExplainRequest request) {
        validateExplainRequest(request);

        String narrative = aiService.explain(request.profile(), request.metrics());

        if (narrative == null) {
            throw new InvalidRequestException("AI 서술 생성에 실패했습니다. 최대 재시도 횟수를 초과했습니다.");
        }

        ExplainResponse response = new ExplainResponse(narrative);
        return ApiResponse.of(response);
    }

    private void validateParseGoalRequest(ParseGoalRequest request) {
        if (request == null) {
            throw new InvalidRequestException("요청 본문이 필요합니다.");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new InvalidRequestException("text 필드는 필수입니다.");
        }
    }

    private void validateExplainRequest(ExplainRequest request) {
        if (request == null) {
            throw new InvalidRequestException("요청 본문이 필요합니다.");
        }
        if (request.profile() == null || request.profile().isBlank()) {
            throw new InvalidRequestException("profile 필드는 필수입니다.");
        }
        if (request.metrics() == null || request.metrics().isEmpty()) {
            throw new InvalidRequestException("metrics 필드는 필수입니다.");
        }
    }
}
