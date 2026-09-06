package com.divurve.engine.volatility;

import com.divurve.engine.EngineComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * {@code GET /market/regime} 의 판정 근거 {@code checks} 산출 (API 명세 v2 §5.10).
 *
 * <p>v1 의 안전모드 6조건({@code engine/safemode})을 대체한다. 명세 §0.1 이
 * {@code 503 SAFE_MODE_ACTIVE} 를 삭제하면서 "급변 상태에서도 최신 정보를 숨기지 않는다"(FR-SF-01)를
 * 근거로 들었으므로, <b>여기 판정은 무엇도 차단하지 않는다</b> — 사용자에게 보여줄 근거일 뿐이다.
 * v1 6조건 중 §5.10 {@code checks} 에 근거가 있는 세 가지만 남겼다.
 * <ul>
 *   <li>{@code data_freshness} ← v1 {@code data_staleness}</li>
 *   <li>{@code source_divergence} ← v1 {@code source_discrepancy}</li>
 *   <li>{@code vol_percentile} ← {@link RegimeClassifier} 경계 재사용</li>
 * </ul>
 * 나머지 셋({@code rate_deviation} · {@code coverage_shortfall} · {@code consecutive_skip})은
 * v2 문서에 근거가 없어 버렸다.
 *
 * <p><b>순수 함수다.</b> v1 {@code SafeModeEvaluator} 는 {@code LocalDate.now()} 를 직접 불러
 * 같은 입력이 날짜에 따라 다른 결과를 내는 재현 불가 상태였다. 기준일은 인자로 받는다.
 */
@EngineComponent
public class MarketChecks {

    /** {@code checks[].key} — 데이터 신선도. */
    public static final String KEY_DATA_FRESHNESS = "data_freshness";

    /** {@code checks[].key} — 출처 간 괴리. */
    public static final String KEY_SOURCE_DIVERGENCE = "source_divergence";

    /** {@code checks[].key} — 변동성 백분위. */
    public static final String KEY_VOL_PERCENTILE = "vol_percentile";

    /** 마지막 갱신 이후 이 일수 이상이면 신선도 판정 실패. ⚠️ 문서 미확정 — v1 임계값(3일) 승계. */
    public static final long DATA_FRESHNESS_MAX_DAYS = 3L;

    /** 출처 간 상대 괴리가 이 비율 이상이면 판정 실패. ⚠️ 문서 미확정 — v1 임계값(5%) 승계. */
    public static final double SOURCE_DIVERGENCE_MAX_RATIO = 0.05;

    /**
     * 판정 항목 하나 (명세 §5.10 {@code checks[]}).
     *
     * @param key 항목 키
     * @param passed 통과 여부. {@code false} 여도 기능을 끄지 않는다 (FR-SF-01)
     * @param detail 실패 사유. 통과 시 {@code null}
     */
    public record Check(String key, boolean passed, String detail) {
    }

    /**
     * 데이터 신선도 — 마지막 갱신 이후 경과일이 {@value #DATA_FRESHNESS_MAX_DAYS}일 미만이어야 통과.
     *
     * @param lastUpdateDate 마지막 시장 데이터 갱신일. {@code null} 이면 이력 없음으로 보고 실패
     * @param asOfDate 기준일 (응답 {@code meta.as_of} 와 같은 기준). 현재 시각을 직접 읽지 않는다
     * @return 판정 결과
     * @throws NullPointerException asOfDate 가 null 인 경우
     */
    public Check dataFreshness(LocalDate lastUpdateDate, LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        if (lastUpdateDate == null) {
            return new Check(KEY_DATA_FRESHNESS, false, "시장 데이터 갱신 이력이 없습니다.");
        }

        long daysPassed = ChronoUnit.DAYS.between(lastUpdateDate, asOfDate);
        boolean passed = daysPassed < DATA_FRESHNESS_MAX_DAYS;
        return new Check(
            KEY_DATA_FRESHNESS,
            passed,
            passed ? null : "마지막 갱신 이후 %d일 경과했습니다.".formatted(daysPassed));
    }

    /**
     * 출처 간 괴리 — 두 출처 환율의 상대 차이가
     * {@value #SOURCE_DIVERGENCE_MAX_RATIO} 미만이어야 통과.
     *
     * <p>비교 출처가 없으면(단일 출처) 판정 대상이 아니므로 통과로 둔다. 없는 근거를 만들어내지 않는다
     * (FR-CM-10, 명세 §1.2 {@code sources}).
     *
     * @param primaryRate 주 출처 환율. {@code null} 이면 판정 생략
     * @param secondaryRate 비교 출처 환율. {@code null} 이면 판정 생략
     * @return 판정 결과
     * @throws IllegalArgumentException 두 환율 중 하나라도 0 이하인 경우
     */
    public Check sourceDivergence(BigDecimal primaryRate, BigDecimal secondaryRate) {
        if (primaryRate == null || secondaryRate == null) {
            return new Check(KEY_SOURCE_DIVERGENCE, true, null);
        }
        if (primaryRate.signum() <= 0 || secondaryRate.signum() <= 0) {
            throw new IllegalArgumentException("환율은 0보다 커야 합니다.");
        }

        BigDecimal higher = primaryRate.max(secondaryRate);
        BigDecimal lower = primaryRate.min(secondaryRate);
        double divergence = higher.subtract(lower)
            .divide(lower, 6, RoundingMode.HALF_UP)
            .doubleValue();

        boolean passed = divergence < SOURCE_DIVERGENCE_MAX_RATIO;
        return new Check(
            KEY_SOURCE_DIVERGENCE,
            passed,
            passed ? null : "출처 간 환율 차이가 %.2f%%입니다.".formatted(divergence * 100));
    }

    /**
     * 변동성 백분위 — {@link RegimeClassifier#ELEVATED_MIN_PERCENTILE} 미만이어야 통과.
     * 즉 {@code calm}·{@code normal} 은 통과, {@code elevated}·{@code stress} 는 실패다.
     *
     * <p>실패 사유 문구는 명세 §5.10 예시 형식을 따른다 —
     * {@code vol_percentile_5y = 0.72} 면 "USDKRW 30일 변동성이 5년 상위 28% 구간입니다."
     *
     * @param pairCode 통화쌍 (예 {@code USDKRW})
     * @param volPercentile5y 5년 변동성 백분위, 0~1 비율
     * @return 판정 결과
     * @throws NullPointerException pairCode 가 null 인 경우
     * @throws IllegalArgumentException volPercentile5y 가 0~1 범위를 벗어나거나 NaN 인 경우
     */
    public Check volPercentile(String pairCode, double volPercentile5y) {
        Objects.requireNonNull(pairCode, "pairCode must not be null");
        if (Double.isNaN(volPercentile5y) || volPercentile5y < 0.0 || volPercentile5y > 1.0) {
            throw new IllegalArgumentException(
                "vol_percentile_5y 는 0~1 비율이어야 합니다: %s".formatted(volPercentile5y));
        }

        boolean passed = volPercentile5y < RegimeClassifier.ELEVATED_MIN_PERCENTILE;
        return new Check(
            KEY_VOL_PERCENTILE,
            passed,
            passed ? null : "%s 30일 변동성이 5년 상위 %.0f%% 구간입니다."
                .formatted(pairCode, (1.0 - volPercentile5y) * 100));
    }
}
