package com.divurve.api.dto.plan;

import com.divurve.domain.plan.PlanStepExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 회차 건너뛰기 응답 (플래너 명세 §15). <b>변경 계획 미리보기이며 아무것도 저장되지 않았다.</b>
 *
 * <p>명세 §15 는 건너뛰기가 현재 계획을 즉시 덮어쓰지 않도록 요구한다 — 남은 회차의 부담이
 * 늘어나는 것을 받아들일지는 사용자가 정할 문제다. 새 회차 금액이 예산을 넘으면 자동 적용하지
 * 않고 조정 선택지를 함께 낸다.
 *
 * @param seq              건너뛸 회차 번호
 * @param applied          적용 여부. 항상 {@code false} — 승인 전에는 계획이 바뀌지 않는다 (§21-9)
 * @param amountBefore     건너뛰기 전 회차 금액
 * @param amountAfter      재분배 후 남은 회차당 금액
 * @param remainingAmount  재분배 후 남은 외화
 * @param remainingRounds  재분배를 받을 회차 수
 * @param adjustmentOptions 조정 선택지 (명세 §15). 재분배할 회차가 없을 때만 채워진다
 */
@Schema(description = "회차 건너뛰기 미리보기 (플래너 명세 §15)")
public record StepSkipResponse(
        int seq,
        boolean applied,
        double amountBefore,
        double amountAfter,
        double remainingAmount,
        int remainingRounds,
        List<String> adjustmentOptions) {

    /** 명세 §15 가 열거한 네 가지 조정 선택지. */
    private static final List<String> ADJUSTMENT_OPTIONS = List.of(
            "CHANGE_ROUND_BUDGET",
            "CHANGE_TARGET_AMOUNT",
            "CHANGE_TARGET_DATE",
            "PAUSE_PLAN");

    /** 도메인 미리보기를 응답으로 옮긴다. */
    public static StepSkipResponse from(PlanStepExecutionService.SkipPreview preview) {
        return new StepSkipResponse(
                preview.seq(),
                false,
                preview.amountBefore(),
                preview.amountAfter(),
                preview.remainingAmount(),
                preview.remainingRounds(),
                preview.exhausted() ? ADJUSTMENT_OPTIONS : List.of());
    }
}
