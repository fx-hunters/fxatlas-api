package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 상세 진단 제출 응답 (API 명세 v2 §5.2 {@code POST /me/risk-profile/detail}).
 * 조회 응답과 같은 필드에 {@code applied}(설정에 즉시 반영된 값)만 더한 형태다.
 *
 * <p>{@code grade}·{@code score} 는 <b>어떤 경우에도 변하지 않는다</b>(FR-DG-05) — 상세 진단은 제목 수식어와
 * 설명 선호에만 반영된다.
 */
@Schema(description = "상세 진단 제출 결과. 점수·유형은 변하지 않는다")
public record RiskProfileDetailResponse(
        @Schema(description = "진단 상태", example = "simple_done",
                allowableValues = {"not_measured", "simple_done", "detail_done"})
        String status,

        @Schema(description = "대표 유형 코드. 미측정이면 null", example = "balanced", nullable = true)
        String grade,

        @Schema(description = "대표 유형 한글 표기. 미측정이면 null", example = "균형항로형", nullable = true)
        String gradeLabel,

        @Schema(description = "Q1~Q3 합계 원점수(0~9). 상세 진단은 이 값을 바꾸지 않는다", example = "4",
                minimum = "0", maximum = "9", nullable = true)
        Integer score,

        @Schema(description = "대표 유형 확정일. 미측정이면 null", example = "2026-09-01", nullable = true)
        LocalDate diagnosedOn,

        @Schema(description = "유형별 집중도 참고 기준선(0~1). 미측정이면 null", example = "0.6", nullable = true)
        Double concentrationThreshold,

        @Schema(description = "간편 진단(Q1~Q3) 응답과 근거 — 상세 진단으로 바뀌지 않는다")
        RiskProfileResponse.Simple simple,

        @Schema(description = "상세 진단 진행 상태")
        RiskProfileResponse.Detail detail,

        @Schema(description = "설정에 즉시 반영된 값 (명세 §5.2 applied)")
        Applied applied,

        @Schema(description = "MVP 가설 한계 고지",
                example = "이 판정은 해커톤 MVP용 가설이며 통계적으로 검증된 금융회사 표준 진단이 아닙니다.")
        String limitationNote) {

    /**
     * 상세 진단이 설정에 반영한 결과 (명세 §5.2 {@code applied}).
     * Q5 는 {@code user_settings.explain_level}, Q6 는 {@code explain_domain} 에 즉시 반영된다.
     */
    @Schema(description = "상세 진단이 사용자 설정에 반영한 값")
    public record Applied(
            @Schema(description = "반영된 설명 선호", example = "standard",
                    allowableValues = {"simple", "standard", "detailed"})
            String explainLevel,

            @Schema(description = "반영된 익숙한 설명 분야", example = "finance",
                    allowableValues = {"finance", "dev", "marketing", "plain"})
            String explainDomain,

            @Schema(description = "Q4 에서 파생된 결과 제목 수식어. Q4 미응답이면 null",
                    example = "지출 균형을 함께 고려하는", nullable = true)
            String titleModifier) {
    }

    /** 조회 응답과 상세 제출 결과를 같은 필드 구성으로 유지하기 위한 편의 생성자. */
    public static RiskProfileDetailResponse of(RiskProfileResponse profile, Applied applied) {
        return new RiskProfileDetailResponse(
                profile.status(),
                profile.grade(),
                profile.gradeLabel(),
                profile.score(),
                profile.diagnosedOn(),
                profile.concentrationThreshold(),
                profile.simple(),
                profile.detail(),
                applied,
                profile.limitationNote());
    }
}
