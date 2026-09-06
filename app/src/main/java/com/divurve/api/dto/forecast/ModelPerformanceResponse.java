package com.divurve.api.dto.forecast;

import com.divurve.domain.forecast.ForecastService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 모델 성적표 응답 ({@code GET /forecast/model-performance}, 명세 v2 §5.8, FR-FC-11).
 *
 * <p><b>성적이 나빠도 그대로 보여준다.</b> {@code rw_improvement} 가 음수여도, {@code hit_rate} 가
 * 50퍼센트에 가까워도 가리지 않는다(ERD §8). {@code coverage_80} 은 반드시 {@code avg_width} 와
 * 함께 노출한다 — 구간을 넓히면 포함률은 얼마든지 올라가므로 폭 없이는 성적이 아니다.
 *
 * <p>v1 대비: 하드코딩 목값 대신 실제 롤링 워크포워드 검증 결과이며,
 * {@code rw_improvement} · {@code evaluated_at} 이 신설됐다.
 */
@Schema(description = "모델 성적표. 성적이 나빠도 그대로 노출한다.")
public record ModelPerformanceResponse(
        @Schema(example = "USDKRW") String pairCode,
        @Schema(example = "30") int horizonDays,
        @Schema(description = "모델 지표") Model model,
        @Schema(description = "랜덤워크 벤치마크") RandomWalk randomWalk,

        @Schema(description = "랜덤워크 대비 MAE 개선율. 음수여도 그대로 노출한다.", example = "0.0")
        double rwImprovement,

        @Schema(description = "검증 방법") Validation validation,
        @Schema(description = "포함률 해석 주의") String note,
        @Schema(description = "평가 기준 시각") Instant evaluatedAt) {

    /** 도메인 뷰를 응답 DTO 로 옮긴다. */
    public static ModelPerformanceResponse from(ForecastService.ModelPerformanceView view) {
        return new ModelPerformanceResponse(
                view.pairCode(),
                view.horizonDays(),
                new Model(
                        view.model().hitRate(),
                        view.model().mae(),
                        view.model().coverage80(),
                        view.model().avgWidth()),
                new RandomWalk(view.randomWalk().hitRate(), view.randomWalk().mae()),
                view.rwImprovement(),
                new Validation(
                        view.validation().method(),
                        view.validation().folds(),
                        view.validation().leakageGuard()),
                view.note(),
                view.evaluatedAt());
    }

    /**
     * 모델 지표.
     *
     * @param hitRate    방향 적중률 (0~1)
     * @param mae        상대 평균 절대 오차
     * @param coverage80 80퍼센트 구간 포함률
     * @param avgWidth   상대 평균 구간 폭
     */
    @Schema(description = "모델 지표. coverage_80 은 avg_width 와 함께 봐야 한다.")
    public record Model(
            @Schema(example = "0.54") double hitRate,
            @Schema(example = "0.019") double mae,
            @Schema(example = "0.81") double coverage80,
            @Schema(example = "0.058") double avgWidth) {
    }

    /**
     * 랜덤워크 벤치마크.
     *
     * @param hitRate 방향 적중률
     * @param mae     상대 평균 절대 오차
     */
    @Schema(description = "랜덤워크 벤치마크 (직전 실측값을 예측으로 쓰는 기준 모형)")
    public record RandomWalk(
            @Schema(example = "0.5") double hitRate,
            @Schema(example = "0.0194") double mae) {
    }

    /**
     * 검증 방법.
     *
     * @param method       검증 방식 코드
     * @param folds        실제 평가한 폴드 수
     * @param leakageGuard 미래 누출 차단 여부
     */
    @Schema(description = "검증 방법")
    public record Validation(
            @Schema(allowableValues = "rolling_walk_forward", example = "rolling_walk_forward")
            String method,
            @Schema(example = "24") int folds,
            @Schema(example = "true") boolean leakageGuard) {
    }
}
