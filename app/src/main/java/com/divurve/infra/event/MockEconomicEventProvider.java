package com.divurve.infra.event;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.EconomicEventProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 경제 이벤트 Mock 제공자.
 *
 * <p>실제 외부 캘린더 API(예: Trading Economics, ForexFactory)가 통합될 때까지
 * 정적 이벤트를 반환한다.
 *
 * <p>향후 실제 외부 API와 교체 예정.
 */
@ExternalAdapter
public class MockEconomicEventProvider implements EconomicEventProvider {

    @Override
    public List<EconomicEvent> fetchUpcoming(LocalDate startDate, int days) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }

        LocalDate endDate = startDate.plusDays(days);

        // 목형 데이터: 샘플 경제 이벤트들
        return Stream.of(
            new EconomicEvent(startDate.plusDays(3), "Federal Funds Rate Decision", "USD", "High"),
            new EconomicEvent(startDate.plusDays(5), "Non-Farm Payroll", "USD", "High"),
            new EconomicEvent(startDate.plusDays(10), "Consumer Price Index", "USD", "High"),
            new EconomicEvent(startDate.plusDays(12), "Retail Sales", "USD", "Medium"),
            new EconomicEvent(startDate.plusDays(15), "Bank of Japan Rate Decision", "JPY", "High"),
            new EconomicEvent(startDate.plusDays(20), "ECB Interest Rate Decision", "EUR", "High"),
            new EconomicEvent(startDate.plusDays(25), "ISM Manufacturing PMI", "USD", "Medium"),
            new EconomicEvent(startDate.plusDays(30), "GDP Preliminary Estimate", "USD", "High")
        )
            .filter(e -> !e.date().isBefore(startDate) && !e.date().isAfter(endDate))
            .toList();
    }
}
