package com.divurve.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 환율 스냅샷 값 객체.
 *
 * <p>NFR-DT-01/02: 모든 수치는 출처·기준 시각을 함께 전달한다.
 * 어댑터는 값을 "생성"하지 않고 외부 출처(source)의 일별 종가(asOf)를 그대로 담아 반환한다.
 *
 * @param pairCode  통화쌍 코드 (예: USD_KRW)
 * @param rate      환율 (일별 종가)
 * @param asOf      종가 기준 영업일
 * @param source    출처 식별자 (예: ECOS)
 * @param fetchedAt 조회 시각 (캐시 판단 및 감사용)
 */
public record RateSnapshot(
    String pairCode,
    BigDecimal rate,
    LocalDate asOf,
    String source,
    Instant fetchedAt
) {
}
