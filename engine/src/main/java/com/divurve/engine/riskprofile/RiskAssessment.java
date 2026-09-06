package com.divurve.engine.riskprofile;

import java.util.List;

/**
 * 간편 진단(Q1~Q3) 산출 결과 (API 명세 v2 §5.1, FR-DG-02·FR-DG-05). 결정론적 계산의 출력이다 —
 * LLM/외부 API 가 아니라 {@link RiskProfileScorer} 가 응답값으로부터 직접 만든다.
 *
 * <p><b>Q4~Q6(상세 진단)은 이 값을 바꾸지 않는다</b>(FR-DG-05). 상세 진단은
 * {@link DetailDiagnosisMapper} 가 제목 수식어·진행 커서만 만든다.
 *
 * @param score                  Q1~Q3 선택지 점수 합계 (0~9)
 * @param riskType               대표 유형 코드 — {@code stable}·{@code balanced}·{@code aggressive}·{@code challenging}
 * @param gradeLabel             대표 유형 한글 표기 (안정항로형·균형항로형·적극항로형·도전항로형)
 * @param concentrationThreshold 유형별 집중도 참고 기준선 (0~1)
 * @param safeRatioAdjust        유형별 안전 버킷 가감 (−1~1)
 * @param mixedResponseNote      상충 응답 보조 설명. 상충이 아니면 {@code null} — 새 유형을 만들지 않는다(FR-DG-06)
 * @param rationale              문항별 판정 근거 (문항 순서대로 3건)
 */
public record RiskAssessment(
        int score,
        String riskType,
        String gradeLabel,
        double concentrationThreshold,
        double safeRatioAdjust,
        String mixedResponseNote,
        List<RiskRationale> rationale) {

    public RiskAssessment {
        rationale = List.copyOf(rationale);
    }
}
