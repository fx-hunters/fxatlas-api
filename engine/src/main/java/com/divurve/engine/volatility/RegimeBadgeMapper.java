package com.divurve.engine.volatility;

import com.divurve.engine.EngineComponent;
import java.util.Collection;
import java.util.Objects;

/**
 * 국면 4종 → 화면 배지 3종 매핑 (API 명세 v2 §2 상태 어휘 매핑).
 *
 * <p><b>매핑 책임은 서버(이 클래스)에 있다.</b> 명세 §2 는 "매핑 책임은 서버에 있으며
 * 클라이언트는 {@code badge} 값을 그대로 그린다"고 못박는다. 클라이언트가 국면 문자열을 보고
 * 스스로 배지를 고르는 경로를 만들면 화면마다 어휘가 갈라진다 — 그 재발 방지가 이 클래스의 존재 이유다.
 *
 * <table border="1">
 *   <caption>명세 §2 고정 매핑표</caption>
 *   <tr><th>{@code fx_stats.regime}</th><th>{@code badge}</th><th>화면 표시</th></tr>
 *   <tr><td>calm</td><td>normal</td><td>정상</td></tr>
 *   <tr><td>normal</td><td>normal</td><td>정상</td></tr>
 *   <tr><td>elevated</td><td>caution</td><td>주의</td></tr>
 *   <tr><td>stress</td><td>turbulent</td><td>급변</td></tr>
 * </table>
 *
 * <p>🔑 어느 배지에서도 <b>응답을 막지 않는다</b>. {@code turbulent} 에서도 {@code /forecast} 는
 * 200 을 내려보내며, 배지는 구간 폭과 불확실성 안내를 넓히는 신호일 뿐이다(FR-SF-01, FR-SF-03).
 * 그래서 이 매퍼에는 "차단"에 해당하는 출력이 없다.
 */
@EngineComponent
public class RegimeBadgeMapper {

    /**
     * 화면 배지 3종 (명세 §2). {@link #code()} 가 API {@code badge},
     * {@link #label()} 이 {@code badge_label} 로 나간다.
     */
    public enum Badge {

        /** 정상 — 일반 데이터 상태 표시. */
        NORMAL("normal", "정상"),

        /** 주의 — 변동성 확대와 이벤트 안내. */
        CAUTION("caution", "주의"),

        /** 급변 — 최신 정보 유지 + 불확실성 경고 + 계획 가정 확인. */
        TURBULENT("turbulent", "급변");

        private final String code;
        private final String label;

        Badge(String code, String label) {
            this.code = code;
            this.label = label;
        }

        /** @return API {@code badge} 값 ({@code normal} / {@code caution} / {@code turbulent}) */
        public String code() {
            return code;
        }

        /** @return 화면 표시 문구 ({@code 정상} / {@code 주의} / {@code 급변}) */
        public String label() {
            return label;
        }
    }

    /**
     * 국면 하나를 배지로 옮긴다 (명세 §2 고정 매핑).
     *
     * @param regime 국면
     * @return 배지
     * @throws NullPointerException regime 이 null 인 경우
     */
    public Badge toBadge(Regime regime) {
        Objects.requireNonNull(regime, "regime must not be null");
        if (regime == Regime.STRESS) {
            return Badge.TURBULENT;
        }
        if (regime == Regime.ELEVATED) {
            return Badge.CAUTION;
        }
        return Badge.NORMAL;
    }

    /**
     * 여러 통화쌍의 국면 중 가장 심각한 것을 고른다.
     *
     * <p>명세 §5.10 은 통화쌍별 {@code pair_regimes} 를 나열하면서 화면 상단에는 배지 하나만 쓴다
     * (예시: USDKRW {@code elevated} · USDJPY {@code normal} · EURUSD {@code calm} → {@code caution}).
     * 그 대표값을 고르는 규칙이 여기다 — 가장 안전한 쪽이 아니라 <b>가장 심각한 쪽</b>을 택한다.
     * 하나라도 급변이면 사용자에게는 급변으로 알린다.
     *
     * @param regimes 통화쌍별 국면 (비어 있으면 안 됨)
     * @return 심각도가 가장 높은 국면
     * @throws NullPointerException regimes 또는 그 원소가 null 인 경우
     * @throws IllegalArgumentException regimes 가 비어 있는 경우
     */
    public Regime worstOf(Collection<Regime> regimes) {
        Objects.requireNonNull(regimes, "regimes must not be null");
        if (regimes.isEmpty()) {
            throw new IllegalArgumentException("regimes 가 비어 있습니다.");
        }

        Regime worst = Regime.CALM;
        for (Regime regime : regimes) {
            Objects.requireNonNull(regime, "regimes must not contain null");
            if (regime.ordinal() > worst.ordinal()) {
                worst = regime;
            }
        }
        return worst;
    }
}
