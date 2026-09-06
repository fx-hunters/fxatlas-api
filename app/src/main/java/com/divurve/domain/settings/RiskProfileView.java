package com.divurve.domain.settings;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 위험성향 진단 조회 결과 (API 명세 v2 §5.1). 트랜잭션 안에서 엔티티를 평탄화해 만든 도메인→웹 전달용 값이다.
 *
 * <p><b>미측정 계약</b>: 진단을 하지 않았거나 Q1~Q3 중 하나라도 비어 있으면 {@code status=not_measured} 이고
 * {@link #riskType()}·{@link #gradeLabel()}·{@link #score()} 가 모두 {@code null} 이다. 예외를 던지지 않는다 —
 * 명세 §5.1 "미진단은 200 + {@code not_measured}"이며, 임의의 기본 성향을 만들지 않는다(FR-DG-02, FR-IS-06).
 *
 * @param status                 {@code not_measured} / {@code simple_done} / {@code detail_done}
 * @param riskType               대표 유형 코드 (grade). 미측정이면 {@code null}
 * @param gradeLabel             대표 유형 한글 표기. 미측정이면 {@code null}
 * @param score                  Q1~Q3 합계 원점수(0~9). 미측정이면 {@code null}
 * @param diagnosedOn            대표 유형 확정일. 미측정이면 {@code null}
 * @param concentrationThreshold 유형별 집중도 참고 기준선. 미측정이면 {@code null}
 * @param simple                 간편 진단 응답·근거
 * @param detail                 상세 진단 진행 상태
 * @param limitationNote         MVP 가설 한계 고지 (FR-DG 마지막 항목)
 */
public record RiskProfileView(
        String status,
        String riskType,
        String gradeLabel,
        Integer score,
        LocalDate diagnosedOn,
        Double concentrationThreshold,
        Simple simple,
        Detail detail,
        String limitationNote) {

    /**
     * 간편 진단(Q1~Q3) 블록.
     *
     * @param answers           문항 코드 → 선택지 코드. 부분 응답도 그대로 담는다
     * @param rationale         문항별 판정 근거. 미측정이면 빈 목록
     * @param mixedResponseNote 상충 응답 보조 설명. 상충이 아니면 {@code null} (FR-DG-06)
     */
    public record Simple(Map<String, String> answers, List<Rationale> rationale, String mixedResponseNote) {
    }

    /** 문항별 판정 근거 (`왜 이렇게 나왔나요?` 아코디언 원본, FR-DG-07). */
    public record Rationale(String question, String choice, int points, String reading) {
    }

    /**
     * 상세 진단(Q4~Q6) 블록. <b>점수·유형에 영향을 주지 않는다</b>(FR-DG-05).
     *
     * @param completed     Q4~Q6 을 모두 채웠는지
     * @param answered      누적 응답
     * @param nextQuestion  재개 커서 — 첫 미응답 문항. 완료면 {@code null} (FR-DG-04)
     * @param titleModifier Q4 에서 파생된 결과 제목 수식어. Q4 미응답이면 {@code null}
     */
    public record Detail(boolean completed, Map<String, String> answered, String nextQuestion, String titleModifier) {
    }
}
