package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.stress.ScenarioListResponse;
import com.divurve.api.dto.stress.StressRunListResponse;
import com.divurve.api.dto.stress.StressRunRequest;
import com.divurve.api.dto.stress.StressRunResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.stress.StressRunService;
import com.divurve.domain.stress.StressScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스트레스 테스트 엔드포인트 (명세 v2 §5.9, 요구사항 §4.8 FR-ST-01~05).
 *
 * <p>v1 의 {@code POST /xray/stress} 는 통화별 환율 충격만 계산하고 저장하지 않았다. v2 는
 * <b>시나리오 마스터 · 실행 저장 · 이력 조회</b> 3개로 나뉜다.
 *
 * <p>🔒 여기서 나오는 수치는 예측이 아니다. 시나리오 충격률은 서버가 보유한 <b>가정값</b>이고,
 * 결과는 그 가정에 대한 조건부 산술이다(FR-ST-04). 응답에 확률도 행동 제안도 담지 않는다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/stress")
@Tag(name = "Stress Test", description = "스트레스 시나리오 마스터·실행·이력 (가정 충격의 조건부 계산)")
public class StressController {

    private final StressScenarioService scenarioService;
    private final StressRunService runService;

    public StressController(StressScenarioService scenarioService, StressRunService runService) {
        this.scenarioService = Objects.requireNonNull(scenarioService, "scenarioService");
        this.runService = Objects.requireNonNull(runService, "runService");
    }

    /** 시나리오 마스터 조회. 기본 2종(주가 하락 + 원화 약세 / 강세)은 시드 데이터다. */
    @Operation(
            summary = "스트레스 시나리오 목록",
            description = "적용 가능한 시나리오와 그 가정 충격률을 노출 순서대로 반환한다(FR-ST-01). "
                    + "충격률은 가정값이며 예측이 아니다 — reference_event·assumption_note 를 함께 노출한다. "
                    + "데이터가 없으면 200 + 빈 배열이다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "시나리오 목록"))
    @GetMapping("/scenarios")
    public ApiResponse<ScenarioListResponse> listScenarios() {
        return ApiResponse.of(ScenarioListResponse.from(scenarioService.listScenarios()));
    }

    /** 시나리오 적용·저장. 결과는 삭제하지 않는다(FR-ST-05). */
    @Operation(
            summary = "스트레스 시나리오 실행",
            description = "시나리오 충격을 주가 → 환율 순서로 적용하고 결과를 저장한다(FR-ST-02·05). "
                    + "적용 순서가 고정이라 equity_effect_krw + fx_effect_krw = total_effect_krw 가 "
                    + "정확히 성립한다. 부호 규약: fx_shock_pct 양수 = USD/KRW 상승 = 원화 약세(FR-CM-05). "
                    + "결과는 예측이 아니라 가정 충격의 조건부 계산이며 응답의 conditional_note 가 이를 명시한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "실행 결과 저장됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "scenario_code 누락 또는 계산 입력 오류 (VALIDATION_FAILED)",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 없음 (UNAUTHORIZED)",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "시나리오 또는 사용자 없음 (NOT_FOUND)",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StressRunResponse> run(
            @CurrentUser UUID userId,
            @RequestBody StressRunRequest request) {
        String scenarioCode = request == null ? null : request.scenarioCode();
        return ApiResponse.of(StressRunResponse.from(runService.run(userId, scenarioCode)));
    }

    /** 실행 이력 조회. 소유자 것만 보인다(NFR-SE-02). */
    @Operation(
            summary = "스트레스 실행 이력",
            description = "본인의 과거 실행을 최신순으로 반환한다(FR-ST-05). 이력은 삭제하지 않으며 "
                    + "충격률은 실행 시점 스냅샷이라 시나리오 마스터가 바뀌어도 과거 수치가 그대로 재현된다. "
                    + "이력이 없으면 200 + 빈 배열이다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "실행 이력"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 없음 (UNAUTHORIZED)",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/runs")
    public ApiResponse<StressRunListResponse> listRuns(@CurrentUser UUID userId) {
        return ApiResponse.of(StressRunListResponse.from(runService.listRuns(userId)));
    }
}
