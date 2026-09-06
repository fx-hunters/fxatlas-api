package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 위험성향 진단 조회 응답 (API 명세 v2 §5.1 {@code GET /me/risk-profile}).
 * 필드명은 명세 그대로 — {@code grade}·{@code grade_label}·{@code score}·{@code concentration_threshold}.
 *
 * <p>미진단은 404 가 아니라 <b>200 + {@code status=not_measured}</b> 이며 {@code grade}·{@code score} 가 {@code null} 이다.
 * 임의의 기본 성향을 채워 넣지 않는다(FR-DG-02, FR-IS-06).
 */
@Schema(description = "위험성향 진단 상태와 판정 근거")
public record RiskProfileResponse(
        @Schema(description = "진단 상태", example = "simple_done",
                allowableValues = {"not_measured", "simple_done", "detail_done"})
        String status,

        @Schema(description = "대표 유형 코드. 미측정이면 null", example = "balanced",
                allowableValues = {"stable", "balanced", "aggressive", "challenging"}, nullable = true)
        String grade,

        @Schema(description = "대표 유형 한글 표기. 미측정이면 null", example = "균형항로형", nullable = true)
        String gradeLabel,

        @Schema(description = "Q1~Q3 합계 원점수(0~9). Q4~Q6 은 이 값을 바꾸지 않는다", example = "4",
                minimum = "0", maximum = "9", nullable = true)
        Integer score,

        @Schema(description = "대표 유형 확정일. 미측정이면 null", example = "2026-09-01", nullable = true)
        LocalDate diagnosedOn,

        @Schema(description = "유형별 집중도 참고 기준선(0~1). 미측정이면 null", example = "0.6", nullable = true)
        Double concentrationThreshold,

        @Schema(description = "간편 진단(Q1~Q3) 응답과 근거")
        Simple simple,

        @Schema(description = "상세 진단(Q4~Q6) 진행 상태")
        Detail detail,

        @Schema(description = "MVP 가설 한계 고지",
                example = "이 판정은 해커톤 MVP용 가설이며 통계적으로 검증된 금융회사 표준 진단이 아닙니다.")
        String limitationNote) {

    /** 간편 진단 블록 (명세 §5.1 {@code simple}). */
    @Schema(description = "간편 진단(Q1~Q3) 응답과 문항별 판정 근거")
    public record Simple(
            @Schema(description = "문항 코드 → 선택지 코드. 부분 응답도 그대로 담는다",
                    example = "{\"q1\":\"B\",\"q2\":\"C\",\"q3\":\"B\"}")
            Map<String, String> answers,

            @Schema(description = "`왜 이렇게 나왔나요?` 아코디언 원본. 미측정이면 빈 목록")
            List<Rationale> rationale,

            @Schema(description = "상충 응답 보조 설명. 상충이 아니면 null — 새 유형을 만들지 않는다(FR-DG-06)",
                    nullable = true)
            String mixedResponseNote) {
    }

    /** 문항별 판정 근거 (명세 §5.1 {@code rationale}). */
    @Schema(description = "문항별 판정 근거")
    public record Rationale(
            @Schema(description = "문항 코드", example = "q1", allowableValues = {"q1", "q2", "q3"})
            String question,

            @Schema(description = "선택지 코드", example = "B", allowableValues = {"A", "B", "C", "D"})
            String choice,

            @Schema(description = "선택지 점수 (A=0·B=1·C=2·D=3)", example = "1", minimum = "0", maximum = "3")
            int points,

            @Schema(description = "선택 내용을 사용자 언어로 옮긴 한 문장",
                    example = "작은 손실은 받아들이지만 커지면 불편하게 느낍니다.")
            String reading) {
    }

    /** 상세 진단 블록 (명세 §5.1 {@code detail}). 점수·유형에 영향을 주지 않는다(FR-DG-05). */
    @Schema(description = "상세 진단(Q4~Q6) 진행 상태. 점수·유형에 영향이 없다")
    public record Detail(
            @Schema(description = "Q4~Q6 을 모두 채웠는지", example = "false")
            boolean completed,

            @Schema(description = "누적 응답", example = "{\"q4\":\"B\"}")
            Map<String, String> answered,

            @Schema(description = "재개 커서 — 첫 미응답 문항. 완료면 null", example = "q5",
                    allowableValues = {"q4", "q5", "q6"}, nullable = true)
            String nextQuestion,

            @Schema(description = "Q4 에서 파생된 결과 제목 수식어. Q4 미응답이면 null",
                    example = "지출 균형을 함께 고려하는", nullable = true)
            String titleModifier) {
    }
}
