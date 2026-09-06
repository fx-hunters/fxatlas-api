package com.divurve.api.dto.plan;

import com.divurve.domain.plan.PlanPreviewService.PlanPreviewInfo;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * PlanPreviewInfo를 API 응답 DTO로 매핑하는 변환기.
 */
@Component
public class PlanPreviewResponseMapper {

    /**
     * 내부 PlanPreviewInfo를 응답 DTO로 변환한다.
     *
     * @param info 내부 미리보기 정보
     * @return 응답 DTO
     */
    public PlanPreviewResponse toResponse(PlanPreviewInfo info) {
        Objects.requireNonNull(info, "PlanPreviewInfo는 null일 수 없습니다");

        return new PlanPreviewResponse(
                new PlanPreviewResponse.Goal(
                        info.goal().kind(),
                        info.goal().purpose(),
                        info.goal().currencyCode()
                ),
                info.unfunded(),
                info.weeks(),
                info.sigmaHorizon(),
                new PlanPreviewResponse.Buckets(
                        info.buckets().safe(),
                        info.buckets().opportunity(),
                        info.buckets().safeRatio(),
                        info.buckets().floor()
                ),
                new PlanPreviewResponse.Split(
                        info.split().count(),
                        info.split().intervalDays(),
                        info.split().gFactor(),
                        new PlanPreviewResponse.Split.NextStepDelta(
                                info.split().nextStepDelta().sigmaGain(),
                                info.split().nextStepDelta().feeIncreaseKrw()
                        )
                ),
                info.steps().stream()
                        .map(step -> new PlanPreviewResponse.Step(
                                step.seq(),
                                step.scheduledDate(),
                                step.amount(),
                                step.krwEstimate(),
                                step.executedAmount(),
                                step.status()
                        ))
                        .toList(),
                new PlanPreviewResponse.Opportunity(
                        info.opportunity().amount(),
                        info.opportunity().triggerRate(),
                        info.opportunity().finalSafeDate(),
                        info.opportunity().note()
                ),
                new PlanPreviewResponse.Metrics(
                        info.metrics().hero(),
                        info.metrics().entrySigma(),
                        info.metrics().entrySigmaOnce(),
                        info.metrics().achieveProb(),
                        info.metrics().achieveProbOnce(),
                        info.metrics().worst5Rate(),
                        new PlanPreviewResponse.Metrics.Fee(
                                info.metrics().fee().spreadKrw(),
                                info.metrics().fee().fixedKrw(),
                                info.metrics().fee().totalKrw()
                        )
                ),
                info.comparison().stream()
                        .map(comp -> new PlanPreviewResponse.Comparison(
                                comp.strategy(),
                                comp.splitCount(),
                                comp.avgRate(),
                                comp.worst5Rate(),
                                comp.feeKrw(),
                                comp.achieveProb()
                        ))
                        .toList(),
                new PlanPreviewResponse.Concentration(
                        info.concentration().before(),
                        info.concentration().after(),
                        info.concentration().threshold(),
                        info.concentration().verdict()
                ),
                info.warnings().stream()
                        .map(warning -> new PlanPreviewResponse.Warning(
                                warning.code(),
                                warning.message()
                        ))
                        .toList()
        );
    }
}
