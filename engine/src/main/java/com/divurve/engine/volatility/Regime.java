package com.divurve.engine.volatility;

/**
 * 시장 변동성 국면 — ERD {@code fx_stats.regime} ENUM 4종과 1:1 대응한다.
 *
 * <p>ERD v3.0: {@code CREATE TYPE vol_regime AS ENUM ('calm', 'normal', 'elevated', 'stress')}.
 * {@link #code()} 가 DB 에 저장되고 API 응답 {@code regime} 필드에 그대로 실린다 —
 * 대소문자를 바꾸거나 축약하지 않는다(CLAUDE.md 5장).
 *
 * <p>선언 순서가 곧 심각도 순서다({@code CALM < NORMAL < ELEVATED < STRESS}).
 * {@link RegimeBadgeMapper#worstOf} 가 이 순서를 근거로 여러 통화쌍의 대표 국면을 고른다.
 */
public enum Regime {

    /** 평온 — 변동성이 5년 분포 하위 구간. */
    CALM("calm"),

    /** 보통 — 평시 범위. */
    NORMAL("normal"),

    /** 확대 — 변동성 확대와 이벤트 안내가 필요한 구간 (API §2 배지 `caution`). */
    ELEVATED("elevated"),

    /** 급변 — 불확실성 경고와 계획 가정 확인이 필요한 구간 (API §2 배지 `turbulent`). */
    STRESS("stress");

    private final String code;

    Regime(String code) {
        this.code = code;
    }

    /**
     * DB·API 에 쓰이는 소문자 코드.
     *
     * @return {@code calm} / {@code normal} / {@code elevated} / {@code stress}
     */
    public String code() {
        return code;
    }
}
