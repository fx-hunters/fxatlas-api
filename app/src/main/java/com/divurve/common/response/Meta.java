package com.divurve.common.response;

import java.time.Instant;
import java.util.List;

/**
 * 모든 API 응답에 함께 실리는 메타 정보 (문서 6장 — data + meta 래핑).
 *
 * <p>{@code timestamp} 는 응답 생성 시각이다. 수치를 반환하는 엔드포인트는 명세(NFR-DT-01)에 따라
 * <b>데이터 기준 시각({@code as_of})</b>과 <b>출처({@code sources})</b>를 반드시 채워야 하며,
 * 이때 {@link #of(Instant, List)} 팩토리를 쓴다. 표시성 응답은 {@link #now()} 로 두 값을 비운다.
 *
 * @param timestamp 응답 생성 시각
 * @param asOf      데이터 기준 시각(수치 응답 필수, 그 외 {@code null})
 * @param sources   데이터 출처 목록(수치 응답 필수, 그 외 빈 목록) — 예: {@code ["ECOS", "FRED"]}
 */
public record Meta(Instant timestamp, Instant asOf, List<String> sources) {

    /** 현재 시각으로 메타를 생성한다. 수치 응답이 아니므로 {@code as_of}·{@code sources} 는 비운다. */
    public static Meta now() {
        return new Meta(Instant.now(), null, List.of());
    }

    /**
     * 수치 응답용 메타를 생성한다. 응답 생성 시각은 현재로 채우고, 데이터 기준 시각·출처를 명시한다(NFR-DT-01).
     *
     * @param asOf    데이터 기준 시각
     * @param sources 데이터 출처 목록
     */
    public static Meta of(Instant asOf, List<String> sources) {
        return new Meta(Instant.now(), asOf, sources == null ? List.of() : List.copyOf(sources));
    }
}
