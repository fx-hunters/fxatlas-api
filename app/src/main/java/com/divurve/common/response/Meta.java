package com.divurve.common.response;

import java.time.Instant;
import java.util.List;

/**
 * 모든 API 응답에 함께 실리는 메타 정보 (API 명세 v2 §1.2). 계산 수치가 포함된 응답은 {@code meta} 가 필수다.
 *
 * <p>직렬화 키는 전역 SNAKE_CASE 전략으로 {@code as_of · data_state · sources · is_demo · regime · model_version}
 * 이 되며, {@code default-property-inclusion: non_null} 설정에 따라 {@code regime}·{@code model_version} 은
 * 값이 있을 때만 응답에 실린다(명세 §5 예시들이 두 필드를 상황에 따라 생략하는 것과 일치). 반면
 * {@code is_demo} 는 원시 boolean 이라 항상 실린다 — 명세 §1.1 "모든 응답의 {@code meta.is_demo}".
 *
 * <p><b>FR-CM-10</b>: {@code sources} 는 {@code data_state=live} 일 때만 채운다. Mock 단계에서는 출처를
 * 만들어내지 않으므로, 이 레코드는 {@code data_state=mock} 이면 {@code sources} 를 강제로 빈 목록으로 만든다.
 *
 * @param asOf         데이터 기준 시각(UTC). 수치를 반환하는 모든 응답에 필수 — FR-CM-01, NFR-DT-01
 * @param dataState    {@link #LIVE} 또는 {@link #MOCK} — FR-CM-02, NFR-DT-03
 * @param sources      데이터 출처 목록. {@code live} 일 때만 채운다 — FR-CM-10
 * @param isDemo       데모(둘러보기) 계정 여부 — FR-IS-09
 * @param regime       시장 국면 {@code calm/normal/elevated/stress}. 시장 수치 동반 응답에만 — FR-SF-02
 * @param modelVersion 예측 모델 버전. {@code /forecast} 계열에만 — FR-FC-11
 */
public record Meta(
        Instant asOf,
        String dataState,
        List<String> sources,
        boolean isDemo,
        String regime,
        String modelVersion) {

    /** {@code data_state} — 실데이터. 이때만 {@code sources} 를 채운다. */
    public static final String LIVE = "live";

    /** {@code data_state} — 시연용 Mock 데이터. 클라이언트가 `시연용 예시 데이터` 배지를 노출한다. */
    public static final String MOCK = "mock";

    public Meta {
        // 출처를 만들어내지 않는다(FR-CM-10). mock 이면 무엇이 들어와도 빈 목록으로 고정한다.
        sources = (sources == null || MOCK.equals(dataState)) ? List.of() : List.copyOf(sources);
    }

    /**
     * Mock 데이터 응답용 메타. {@code sources} 는 항상 비어 있다(FR-CM-10).
     *
     * @param asOf 데이터 기준 시각
     */
    public static Meta mock(Instant asOf) {
        return new Meta(asOf, MOCK, List.of(), false, null, null);
    }

    /**
     * 실데이터 응답용 메타.
     *
     * @param asOf    데이터 기준 시각
     * @param sources 데이터 출처 목록 (예: {@code ["ECOS", "FRED"]}). {@code null} 이면 빈 목록
     */
    public static Meta live(Instant asOf, List<String> sources) {
        return new Meta(asOf, LIVE, sources, false, null, null);
    }

    /** 데모 계정 여부를 바꾼 새 메타를 만든다(FR-IS-09). */
    public Meta withDemo(boolean isDemo) {
        return new Meta(asOf, dataState, sources, isDemo, regime, modelVersion);
    }

    /** 시장 국면을 붙인 새 메타를 만든다. 값은 {@code calm/normal/elevated/stress} (FR-SF-02). */
    public Meta withRegime(String regime) {
        return new Meta(asOf, dataState, sources, isDemo, regime, modelVersion);
    }

    /** 예측 모델 버전을 붙인 새 메타를 만든다. {@code /forecast} 계열에만 쓴다(FR-FC-11). */
    public Meta withModelVersion(String modelVersion) {
        return new Meta(asOf, dataState, sources, isDemo, regime, modelVersion);
    }
}
