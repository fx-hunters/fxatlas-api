package com.divurve.api.dto.stress;

import com.divurve.domain.stress.StressRunService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 스트레스 실행 이력 ({@code GET /stress/runs}, 명세 v2 §3 FR-ST-05).
 *
 * <p>이력은 <b>삭제하지 않는다</b> — 사용자에게 이미 노출된 계산 근거이기 때문이다(ERD §12).
 * 충격률은 실행 시점 스냅샷이라 시나리오 마스터가 바뀌어도 과거 수치가 그대로 재현된다.
 *
 * <p>적용 전/후 평가액은 저장돼 있지 않으므로 이력에는 효과 3항만 싣는다 —
 * 없는 값을 만들어내지 않는다(FR-CM-10).
 */
@Schema(description = "스트레스 실행 이력. 삭제하지 않으며 충격률은 실행 시점 스냅샷이다.")
public record StressRunListResponse(
        @Schema(description = "최신순 실행 이력. 없으면 빈 배열.")
        List<Run> runs) {

    /** 도메인 뷰를 응답 DTO 로 옮긴다. */
    public static StressRunListResponse from(List<StressRunService.RunHistoryView> views) {
        return new StressRunListResponse(views.stream().map(Run::from).toList());
    }

    /**
     * 과거 실행 한 건.
     *
     * @param id        실행 id
     * @param scenario  시나리오 요약 (마스터에서 사라졌으면 null)
     * @param baseDate  계산 기준일
     * @param shock     실행 시점 충격값 스냅샷
     * @param effects   효과 3항
     * @param createdAt 실행 시각
     */
    @Schema(description = "과거 실행 한 건")
    public record Run(
            UUID id,
            StressRunResponse.Scenario scenario,
            LocalDate baseDate,
            StressRunResponse.Shock shock,
            StressRunResponse.Effects effects,
            Instant createdAt) {

        static Run from(StressRunService.RunHistoryView view) {
            return new Run(
                    view.id(),
                    StressRunResponse.Scenario.from(view.scenario()),
                    view.baseDate(),
                    new StressRunResponse.Shock(view.equityShockPct(), view.fxShockPct()),
                    new StressRunResponse.Effects(
                            view.equityEffectKrw(), view.fxEffectKrw(), view.totalEffectKrw()),
                    view.createdAt());
        }
    }
}
