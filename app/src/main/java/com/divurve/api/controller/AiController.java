package com.divurve.api.controller;

import com.divurve.api.dto.ai.ExplainRequest;
import com.divurve.api.dto.ai.ExplainResponse;
import com.divurve.api.dto.ai.ParseGoalRequest;
import com.divurve.api.dto.ai.ParseGoalResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 엔드포인트 스텁 (명세 4장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 * AI 는 구조화·서술만 하며 금액·확률·비용을 계산하지 않는다 (NFR-AI-01).
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "자연어 목표 구조화·엔진 결과 서술")
public class AiController {

    @Operation(summary = "자연어 문장을 목표 제약으로 구조화")
    @PostMapping("/parse-goal")
    public ApiResponse<ParseGoalResponse> parseGoal(@RequestBody ParseGoalRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "엔진 결과를 설명 프로필에 맞춰 서술")
    @PostMapping("/explain")
    public ApiResponse<ExplainResponse> explain(@RequestBody ExplainRequest request) {
        throw new NotImplementedException();
    }
}
