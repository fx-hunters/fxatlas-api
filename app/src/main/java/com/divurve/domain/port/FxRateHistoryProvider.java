package com.divurve.domain.port;

import java.time.LocalDate;
import java.util.List;

/**
 * 환율 히스토리 조회 포트 (팬차트 생성용).
 *
 * <p>도메인은 이 인터페이스만 알며, 구현체(infra)는 알지 못한다 (DIP).
 * 일별 종가 기반 수익률 계산에 필요한 과거 환율들을 조회한다.
 */
public interface FxRateHistoryProvider {

    /**
     * 지정 통화쌍의 과거 일별 환율을 조회한다.
     *
     * @param pairCode 통화쌍 코드 (예: USD_KRW)
     * @param endDate 조회 끝 날짜 (포함)
     * @param days 과거 일수 (예: 5*252 = 5년)
     * @return 일별 환율들 (시간순, 가장 오래된 것부터)
     */
    List<HistoryRateSnapshot> fetchHistorical(String pairCode, LocalDate endDate, int days);

    /**
     * 과거 환율 스냅샷.
     *
     * @param date 해당 환율의 기준 날짜
     * @param rate 환율
     */
    record HistoryRateSnapshot(LocalDate date, Double rate) {
    }
}
