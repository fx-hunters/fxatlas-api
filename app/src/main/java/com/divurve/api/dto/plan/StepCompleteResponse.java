package com.divurve.api.dto.plan;

import com.divurve.domain.plan.PlanStepExecutionService;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 회차 완료 응답 (플래너 명세 §14).
 *
 * @param seq             회차 번호
 * @param status          회차 상태 (명세 §13.2)
 * @param executedAmount  실행한 외화 금액
 * @param executedRate    실행 환율
 * @param executedDate    실행일
 * @param remainingAmount 남은 외화 금액 {@code max(target - 실행합, 0)}
 * @param nextActionSeq   지금 확인·기록할 다음 회차. 남은 회차가 없으면 {@code null}
 * @param alreadyApplied  이미 반영된 요청의 재전송이었는지. 참이면 아무것도 저장되지 않았다 (§21-12)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "회차 완료 응답 (플래너 명세 §14)")
public record StepCompleteResponse(
        int seq,
        String status,
        double executedAmount,
        Double executedRate,
        LocalDate executedDate,
        double remainingAmount,
        Integer nextActionSeq,
        boolean alreadyApplied) {

    /** 도메인 결과를 응답으로 옮긴다. */
    public static StepCompleteResponse from(PlanStepExecutionService.CompleteResult result) {
        return new StepCompleteResponse(
                result.seq(),
                result.status(),
                result.executedAmount(),
                result.executedRate(),
                result.executedDate(),
                result.remainingAmount(),
                result.nextActionSeq(),
                result.alreadyApplied());
    }
}
