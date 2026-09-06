package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 간편 진단 제출 요청 (API 명세 v2 §3 {@code POST /me/risk-profile/simple}, FR-DG-01·FR-DG-04).
 * 답한 문항만 보내면 되며(부분 제출), Q1~Q3 이 모두 채워졌을 때만 유형이 산출된다(FR-DG-02).
 */
@Schema(description = "간편 진단(Q1~Q3) 응답. 부분 제출 허용")
public record RiskProfileSimpleRequest(
        @Schema(description = "문항 코드(q1~q3) → 선택지 코드(A~D)",
                example = "{\"q1\":\"B\",\"q2\":\"C\",\"q3\":\"B\"}")
        Map<String, String> answers) {
}
