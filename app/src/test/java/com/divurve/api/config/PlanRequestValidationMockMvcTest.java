package com.divurve.api.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.divurve.api.controller.PlanController;
import com.divurve.api.dto.plan.PlanPreviewResponseMapper;
import com.divurve.domain.plan.PlanAccessService;
import com.divurve.domain.plan.PlanConfirmService;
import com.divurve.domain.plan.PlanPreviewService;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.PlanStepExecutionService;
import com.divurve.domain.plan.PlanStepRepository;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.DataSourceStatus;
import com.divurve.domain.port.TokenProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 이슈 #75 — {@code POST /plans/preview} 에 {@code goal_id} 없이 요청하면 이전에는 컨트롤러가
 * {@code UUID.fromString(null)} 을 호출해 {@code NullPointerException} → 500 으로 샜다.
 * {@code @NotBlank} + {@code @Valid} 가 실제 요청 경로에서 그보다 먼저 400 으로 막는지 확인한다.
 *
 * <p>{@code @Valid} 는 메서드 인자 바인딩 단계에서 걸리므로 소유자 검증·계산에 진입하기 전에
 * 400 으로 끝난다 — 협력자에 아무 상호작용이 없는지로 그 순서를 확인한다.
 */
@WebMvcTest(PlanController.class)
class PlanRequestValidationMockMvcTest {

    private static final String AUTH_HEADER = "Bearer valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlanAccessService planAccessService;

    @MockBean
    private PlanRepository planRepository;

    @MockBean
    private PlanStepRepository planStepRepository;

    @MockBean
    private PlanConfirmService planConfirmService;

    @MockBean
    private PlanStepExecutionService planStepExecutionService;

    @MockBean
    private PlanPreviewService planPreviewService;

    @MockBean
    private PlanPreviewResponseMapper planPreviewResponseMapper;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean
    private DataSourceStatus dataSourceStatus;

    @BeforeEach
    void authenticate() {
        when(tokenProvider.verify(anyString()))
                .thenReturn(Optional.of(new AuthPrincipal(UUID.randomUUID(), false)));
    }

    @Test
    void 목표_ID가_없으면_500이_아니라_400_VALIDATION_FAILED_이고_소유자_검증_전에_막힌다() throws Exception {
        mockMvc.perform(post("/api/v1/plans/preview")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekly_budget_krw\":10000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.field").value("goal_id"));

        verifyNoInteractions(planAccessService, planPreviewService);
    }
}
