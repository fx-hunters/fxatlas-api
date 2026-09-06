package com.divurve.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.stress.ScenarioListResponse;
import com.divurve.api.dto.stress.StressRunListResponse;
import com.divurve.api.dto.stress.StressRunRequest;
import com.divurve.api.dto.stress.StressRunResponse;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.stress.StressRunService;
import com.divurve.domain.stress.StressScenarioService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StressController")
class StressControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 1);
    private static final String SCENARIO_CODE = "equity_down_krw_weak";

    private StressScenarioService scenarioService;
    private StressRunService runService;
    private StressController controller;

    @BeforeEach
    void setUp() {
        scenarioService = mock(StressScenarioService.class);
        runService = mock(StressRunService.class);
        controller = new StressController(scenarioService, runService);
    }

    @Test
    @DisplayName("시나리오 목록을 그대로 옮긴다")
    void listScenarios() {
        when(scenarioService.listScenarios()).thenReturn(List.of(
                new StressScenarioService.ScenarioView(
                        SCENARIO_CODE, "주가 하락 + 원화 약세", -0.20, 0.10,
                        "2020년 3월 변동성 급등 참고", "가정 설명", true, (short) 1)));

        ApiResponse<ScenarioListResponse> response = controller.listScenarios();

        assertEquals(1, response.data().scenarios().size());
        assertEquals(SCENARIO_CODE, response.data().scenarios().get(0).scenarioCode());
        assertEquals(-0.20, response.data().scenarios().get(0).equityShockPct());
        assertTrue(response.data().scenarios().get(0).isDefault());
    }

    @Test
    @DisplayName("실행 결과에 효과 3항과 조건부 계산 안내를 함께 싣는다")
    void run() {
        when(runService.run(USER_ID, SCENARIO_CODE)).thenReturn(runView());

        ApiResponse<StressRunResponse> response =
                controller.run(USER_ID, new StressRunRequest(SCENARIO_CODE));

        assertEquals(-4_000_000L, response.data().effects().equityEffectKrw());
        assertEquals(2_072_000L, response.data().effects().fxEffectKrw());
        assertEquals(-1_928_000L, response.data().effects().totalEffectKrw());
        assertEquals(24_720_000L, response.data().before().fxAssetKrw());
        assertEquals(20_000_000L, response.data().before().equityAssetKrw());
        assertEquals(22_792_000L, response.data().after().fxAssetKrw());
        assertEquals(BASE_DATE, response.data().baseDate());
        assertEquals(StressRunService.CONDITIONAL_NOTE, response.data().conditionalNote());
        assertEquals("fx_cushions_equity_loss", response.data().interpretationCode());
        // 예측 모델을 쓰지 않는 응답이라 model_version 은 싣지 않는다.
        assertNull(response.meta().modelVersion());
    }

    @Test
    @DisplayName("본문이 없으면 서비스가 400 을 내도록 null 을 그대로 넘긴다")
    void runWithoutBody() {
        when(runService.run(eq(USER_ID), isNull()))
                .thenThrow(new InvalidRequestException("scenario_code 는 필수입니다.", "scenario_code"));

        assertThrows(InvalidRequestException.class, () -> controller.run(USER_ID, null));
    }

    @Test
    @DisplayName("이력을 그대로 옮긴다")
    void listRuns() {
        when(runService.listRuns(USER_ID)).thenReturn(List.of(new StressRunService.RunHistoryView(
                UUID.randomUUID(),
                new StressRunService.ScenarioSummary(SCENARIO_CODE, "주가 하락 + 원화 약세", null, null),
                BASE_DATE, -0.20, 0.10, -4_000_000L, 2_072_000L, -1_928_000L,
                Instant.parse("2026-09-01T15:30:00Z"))));

        ApiResponse<StressRunListResponse> response = controller.listRuns(USER_ID);

        assertEquals(1, response.data().runs().size());
        assertEquals(SCENARIO_CODE, response.data().runs().get(0).scenario().scenarioCode());
        assertEquals(-1_928_000L, response.data().runs().get(0).effects().totalEffectKrw());
        assertEquals(0.10, response.data().runs().get(0).shock().fxShockPct());
    }

    @Test
    @DisplayName("마스터에서 사라진 시나리오의 이력은 이름 없이 남는다")
    void listRunsWithMissingScenario() {
        when(runService.listRuns(USER_ID)).thenReturn(List.of(new StressRunService.RunHistoryView(
                UUID.randomUUID(), null, BASE_DATE, -0.30, 0.20,
                -1L, 2L, 1L, Instant.parse("2026-09-01T15:30:00Z"))));

        ApiResponse<StressRunListResponse> response = controller.listRuns(USER_ID);

        assertNull(response.data().runs().get(0).scenario());
    }

    private static StressRunService.RunView runView() {
        return new StressRunService.RunView(
                UUID.randomUUID(),
                new StressRunService.ScenarioSummary(
                        SCENARIO_CODE, "주가 하락 + 원화 약세",
                        "2020년 3월 변동성 급등 참고", "가정 설명"),
                BASE_DATE,
                -0.20,
                0.10,
                20_000_000L,
                24_720_000L,
                -4_000_000L,
                2_072_000L,
                -1_928_000L,
                22_792_000L,
                "fx_cushions_equity_loss");
    }
}
