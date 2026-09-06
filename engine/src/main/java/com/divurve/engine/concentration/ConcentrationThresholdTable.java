package com.divurve.engine.concentration;

import com.divurve.engine.EngineComponent;
import java.util.Map;

/**
 * 위험성향 등급 → 통화 집중도 기준선 매핑 (FR-XR-03 · FR-FT-01, API 명세 v2 §5.3 · §5.5).
 *
 * <p>ERD {@code risk_profiles.concentration_threshold} 가 담을 값을 결정론적으로 만드는 순수 함수다.
 * 이전에는 {@code XrayService}/{@code FitService} 가 {@code 0.35} 를 하드코딩해 성향과 무관하게
 * 같은 기준선을 썼다. 명세 §4 Mock fixture 는 {@code balanced} → {@code 0.60} 을 고정한다.
 *
 * <p><b>등급이 없으면(미측정) 기준선도 없다</b> — {@code null} 을 반환한다. 임의의 기본 성향·기준선을
 * 채워 넣지 않는다(FR-IS-06, FR-DG-02). 이 경우 집중도 상태는 {@code unknown} 이 된다.
 *
 * <p>⚠️ {@code balanced} 이외의 값은 <b>해커톤 MVP 가설값</b>이며 통계적으로 검증된 배분 기준이 아니다
 * (응답의 {@code basis_note} 가 같은 사실을 사용자에게 고지한다). 등급이 공격적일수록 허용 집중도가
 * 높아지는 단조 증가만 보장한다.
 */
@EngineComponent
public class ConcentrationThresholdTable {

    /** 안정항로형 기준선 (MVP 가설값). */
    public static final double STABLE = 0.50;
    /** 균형항로형 기준선. 명세 §4 fixture 가 고정한 유일한 확정값. */
    public static final double BALANCED = 0.60;
    /** 적극항로형 기준선 (MVP 가설값). */
    public static final double AGGRESSIVE = 0.70;
    /** 도전항로형 기준선 (MVP 가설값). */
    public static final double CHALLENGING = 0.80;

    private static final Map<String, Double> BY_GRADE = Map.of(
            "stable", STABLE,
            "balanced", BALANCED,
            "aggressive", AGGRESSIVE,
            "challenging", CHALLENGING);

    /**
     * 등급 코드에 대응하는 집중도 기준선을 반환한다.
     *
     * @param grade 등급 코드 ({@code stable}/{@code balanced}/{@code aggressive}/{@code challenging}).
     *              {@code null} 이면 미측정
     * @return 기준선(0~1). 미측정이거나 모르는 등급이면 {@code null}
     */
    public Double thresholdFor(String grade) {
        if (grade == null) {
            return null;
        }
        return BY_GRADE.get(grade.toLowerCase());
    }
}
