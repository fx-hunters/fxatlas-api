package com.divurve.infra.ai;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.AiProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mock AI 제공자 (@ExternalAdapter).
 * 프로덕션에서는 실제 Claude API 로 대체된다.
 * 목적: 외부 AI API 호출을 시뮬레이션하며 고정 스키마 검증.
 *
 * 필드 매핑:
 * - kind: wealth / income
 * - purpose: education / retirement / travel
 * - currencyCode: USD / EUR / JPY / KRW
 * - targetAmount: 목표 금액 (null 가능)
 * - recurInterval: monthly / quarterly / yearly
 */
@ExternalAdapter
public class MockAiProvider implements AiProvider {

    @Override
    public ExplainResult explain(String profile, Map<String, Object> metrics) {
        Objects.requireNonNull(profile, "profile 은 null 이 아니어야 합니다");
        Objects.requireNonNull(metrics, "metrics 는 null 이 아니어야 합니다");

        String narrative = generateNarrative(profile, metrics);
        return new ExplainResult(narrative);
    }

    @Override
    public ParseResult parseGoal(String text) {
        Objects.requireNonNull(text, "text 는 null 이 아니어야 합니다");

        return new ParseResult(
                "wealth",
                "retirement",
                "USD",
                100000.0,
                "monthly",
                Map.of(
                        "kind", 0.95,
                        "purpose", 0.90,
                        "currencyCode", 0.98,
                        "targetAmount", 0.70,
                        "recurInterval", 0.85),
                List.of());
    }

    /**
     * mock narrative 생성. 실제로는 LLM 에서 반환된다.
     */
    private String generateNarrative(String profile, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();

        if ("concise".equals(profile)) {
            sb.append("귀사의 자산은 ");
            if (metrics.containsKey("total_amount")) {
                sb.append(metrics.get("total_amount")).append(" 규모입니다. ");
            }
            sb.append("위험도는 중간 수준입니다.");
        } else {
            sb.append("귀사의 포트폴리오 평가입니다. ");
            if (metrics.containsKey("total_amount")) {
                sb.append("총 자산은 ").append(metrics.get("total_amount")).append("입니다. ");
            }
            if (metrics.containsKey("risk_score")) {
                sb.append("위험 점수는 ").append(metrics.get("risk_score")).append("입니다. ");
            }
            sb.append("다양한 자산군에 분산되어 있으며, 변동성은 중간 수준입니다.");
        }

        return sb.toString();
    }
}
