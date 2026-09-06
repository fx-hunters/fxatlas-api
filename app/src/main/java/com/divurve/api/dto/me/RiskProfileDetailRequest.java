package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 상세 진단 제출 요청 (API 명세 v2 §5.2 {@code POST /me/risk-profile/detail}, FR-DG-03·FR-DG-05).
 * Q1~Q3 을 다시 묻지 않으며 중단(부분 제출)이 허용된다. <b>점수·유형은 어떤 경우에도 변하지 않는다.</b>
 */
@Schema(description = "상세 진단(Q4~Q6) 응답. 중단 가능 — 답한 문항만 보낸다")
public record RiskProfileDetailRequest(
        @Schema(description = "q4 는 선택지 코드(A~D), q5 는 설명 선호(simple/standard/detailed), "
                + "q6 는 익숙한 분야(finance/dev/marketing/plain)",
                example = "{\"q4\":\"B\",\"q5\":\"standard\"}")
        Map<String, String> answers) {
}
