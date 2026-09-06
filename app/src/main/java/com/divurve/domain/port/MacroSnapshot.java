package com.divurve.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 거시지표 스냅샷 값 객체 (FRED 등).
 *
 * <p>NFR-DT-01/02: 모든 수치는 출처(source)·기준 시각(asOf)을 함께 전달한다.
 *
 * @param seriesId  지표 시리즈 ID (예: DGS10, CPIAUCSL)
 * @param value     지표 값
 * @param asOf      관측 기준일
 * @param source    출처 식별자 (예: FRED)
 * @param fetchedAt 조회 시각
 */
public record MacroSnapshot(
    String seriesId,
    BigDecimal value,
    LocalDate asOf,
    String source,
    Instant fetchedAt
) {
}
