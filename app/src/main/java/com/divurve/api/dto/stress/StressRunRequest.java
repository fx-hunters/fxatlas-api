package com.divurve.api.dto.stress;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 스트레스 시나리오 실행 요청 ({@code POST /stress/runs}, 명세 v2 §5.9).
 *
 * <p>충격률은 <b>요청에 담지 않는다.</b> 시나리오 마스터에 저장된 가정값만 쓰기 때문에
 * 클라이언트가 임의의 충격을 넣어 "예측처럼 보이는" 수치를 만들 수 없다(FR-ST-04).
 *
 * @param scenarioCode 적용할 시나리오 코드 ({@code GET /stress/scenarios} 의 값)
 */
@Schema(description = "스트레스 시나리오 실행 요청. 충격률은 서버의 시나리오 마스터에서만 온다.")
public record StressRunRequest(
        @Schema(description = "적용할 시나리오 코드", example = "equity_down_krw_weak", requiredMode = Schema.RequiredMode.REQUIRED)
        String scenarioCode) {
}
