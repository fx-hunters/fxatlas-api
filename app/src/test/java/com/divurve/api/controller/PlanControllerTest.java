package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.plan.PlanPreviewRequest;
import com.divurve.api.dto.plan.PlanPreviewResponse;
import com.divurve.api.dto.plan.PlanPreviewResponseMapper;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.plan.PlanPreviewService;
import com.divurve.domain.plan.PlanPreviewService.PlanPreviewInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PlanController} — preview 는 요청 필드를 그대로 서비스로 넘기고 결과를 data/meta 로 감싼다.
 * 아직 로직이 없는 나머지 계획 엔드포인트는 501 을 던진다.
 */
@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanPreviewService planPreviewService;

    private final PlanPreviewResponseMapper mapper = new PlanPreviewResponseMapper();

    private PlanController controller() {
        return new PlanController(planPreviewService, mapper);
    }

    private PlanPreviewInfo sampleInfo() {
        return new PlanPreviewInfo(
                new PlanPreviewInfo.GoalSummary("RECURRING", "TRAVEL", "USD"),
                0.0,
                12,
                0.15,
                new PlanPreviewInfo.Buckets(700_000, 300_000, 0.70, 0.70),
                new PlanPreviewInfo.Split(4, 30, 0.6847,
                        new PlanPreviewInfo.Split.NextStepDelta(0.0213, 3_000L)),
                List.of(new PlanPreviewInfo.Step(1, "2026-09-06", 175_000, 175_000L, 0.0, "PENDING")),
                new PlanPreviewInfo.Opportunity(300_000, 1.05, "2027-09-06", "기회 상황에서만 실행"),
                new PlanPreviewInfo.Metrics("achieveProb", 0.05, 0.06, 0.81, 0.72, 0.95,
                        new PlanPreviewInfo.Metrics.Fee(2_450L, 12_000L, 14_450L)),
                List.of(new PlanPreviewInfo.Comparison("LUMP_SUM", 1, 1.0, 0.92, 5_450L, 0.45)),
                new PlanPreviewInfo.Concentration(Map.of("USD", 0.6), Map.of("USD", 0.7), 0.02, "worsens"),
                List.of(new PlanPreviewInfo.Warning("BUDGET_SHORTFALL", "예산이 목표에 부족합니다")));
    }

    @Test
    void preview_는_요청_네_필드를_그대로_서비스에_넘긴다() {
        String goalId = UUID.randomUUID().toString();
        when(planPreviewService.generatePreview(goalId, 250_000L, 0.8, 6)).thenReturn(sampleInfo());

        controller().preview(new PlanPreviewRequest(goalId, 250_000L, 0.8, 6));

        verify(planPreviewService).generatePreview(goalId, 250_000L, 0.8, 6);
    }

    @Test
    void preview_는_생략된_안전비율_분할횟수를_null_그대로_넘겨_서버_권장값을_쓰게_한다() {
        String goalId = UUID.randomUUID().toString();
        when(planPreviewService.generatePreview(goalId, 100_000L, null, null)).thenReturn(sampleInfo());

        ApiResponse<PlanPreviewResponse> response =
                controller().preview(new PlanPreviewRequest(goalId, 100_000L, null, null));

        assertThat(response.data()).isNotNull();
        verify(planPreviewService).generatePreview(goalId, 100_000L, null, null);
    }

    @Test
    void preview_는_도메인_결과를_응답_DTO_로_변환해_data_meta_로_감싼다() {
        String goalId = UUID.randomUUID().toString();
        when(planPreviewService.generatePreview(goalId, 250_000L, null, null)).thenReturn(sampleInfo());

        ApiResponse<PlanPreviewResponse> response =
                controller().preview(new PlanPreviewRequest(goalId, 250_000L, null, null));

        assertThat(response.meta()).isNotNull();

        PlanPreviewResponse data = response.data();
        assertThat(data.goal()).isEqualTo(new PlanPreviewResponse.Goal("RECURRING", "TRAVEL", "USD"));
        assertThat(data.unfunded()).isZero();
        assertThat(data.weeks()).isEqualTo(12);
        assertThat(data.sigmaHorizon()).isEqualTo(0.15);
        assertThat(data.buckets()).isEqualTo(new PlanPreviewResponse.Buckets(700_000, 300_000, 0.70, 0.70));
        assertThat(data.split().count()).isEqualTo(4);
        assertThat(data.split().intervalDays()).isEqualTo(30);
        assertThat(data.split().gFactor()).isEqualTo(0.6847);
        assertThat(data.split().nextStepDelta())
                .isEqualTo(new PlanPreviewResponse.Split.NextStepDelta(0.0213, 3_000L));
        assertThat(data.steps()).containsExactly(
                new PlanPreviewResponse.Step(1, "2026-09-06", 175_000, 175_000L, 0.0, "PENDING"));
        assertThat(data.opportunity())
                .isEqualTo(new PlanPreviewResponse.Opportunity(300_000, 1.05, "2027-09-06", "기회 상황에서만 실행"));
        assertThat(data.metrics().hero()).isEqualTo("achieveProb");
        assertThat(data.metrics().entrySigma()).isEqualTo(0.05);
        assertThat(data.metrics().entrySigmaOnce()).isEqualTo(0.06);
        assertThat(data.metrics().achieveProb()).isEqualTo(0.81);
        assertThat(data.metrics().achieveProbOnce()).isEqualTo(0.72);
        assertThat(data.metrics().worst5Rate()).isEqualTo(0.95);
        assertThat(data.metrics().fee())
                .isEqualTo(new PlanPreviewResponse.Metrics.Fee(2_450L, 12_000L, 14_450L));
        assertThat(data.comparison()).containsExactly(
                new PlanPreviewResponse.Comparison("LUMP_SUM", 1, 1.0, 0.92, 5_450L, 0.45));
        assertThat(data.concentration()).isEqualTo(new PlanPreviewResponse.Concentration(
                Map.of("USD", 0.6), Map.of("USD", 0.7), 0.02, "worsens"));
        assertThat(data.warnings()).containsExactly(
                new PlanPreviewResponse.Warning("BUDGET_SHORTFALL", "예산이 목표에 부족합니다"));
    }

    @Test
    void 협력자가_null_이면_컨트롤러_생성_시점에_실패한다() {
        assertThatThrownBy(() -> new PlanController(null, mapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("PlanPreviewService");

        assertThatThrownBy(() -> new PlanController(planPreviewService, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("PlanPreviewResponseMapper");
    }

    @Test
    void 아직_구현되지_않은_계획_엔드포인트는_501_을_던진다() {
        PlanController controller = controller();

        assertThatThrownBy(() -> controller.createPlan("goal-id", null))
                .isInstanceOf(NotImplementedException.class);
        assertThatThrownBy(() -> controller.listPlanVersions("goal-id"))
                .isInstanceOf(NotImplementedException.class);
        assertThatThrownBy(() -> controller.getActivePlan("goal-id"))
                .isInstanceOf(NotImplementedException.class);
        assertThatThrownBy(() -> controller.completeStep("plan-id", 1, null))
                .isInstanceOf(NotImplementedException.class);
        assertThatThrownBy(() -> controller.skipStep("plan-id", 1))
                .isInstanceOf(NotImplementedException.class);
    }
}
