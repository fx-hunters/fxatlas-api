package com.fxatlas.domain.port;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 환율 스냅샷 값 객체 (뼈대 스텁 — 실제 필드는 도메인 확정 후 보강).
 *
 * @param pairCode 통화쌍 코드 (예: USD_KRW)
 * @param rate     환율
 * @param fetchedAt 조회 시각
 */
public record RateSnapshot(String pairCode, BigDecimal rate, Instant fetchedAt) {
}
