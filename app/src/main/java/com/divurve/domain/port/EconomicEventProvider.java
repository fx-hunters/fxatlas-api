package com.divurve.domain.port;

import java.time.LocalDate;
import java.util.List;

/**
 * 경제 이벤트(경제지표 발표) 조회 포트.
 *
 * <p>도메인은 이 인터페이스만 알며, 구현체(infra)는 알지 못한다 (DIP).
 */
public interface EconomicEventProvider {

    /**
     * 미래 경제 이벤트를 조회한다.
     *
     * @param startDate 조회 시작 날짜
     * @param days 미래 일수 (예: 30, 90)
     * @return 경제 이벤트 목록
     */
    List<EconomicEvent> fetchUpcoming(LocalDate startDate, int days);

    /**
     * 경제 이벤트 정보.
     *
     * @param date 발표 예정 날짜
     * @param title 지표 이름 (예: "Non-Farm Payroll")
     * @param currencyCode 영향 통화 (예: "USD")
     * @param importance 중요도 ("High", "Medium", "Low")
     */
    record EconomicEvent(LocalDate date, String title, String currencyCode, String importance) {
    }
}
