package com.divurve.infra.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.domain.port.EconomicEventProvider.EconomicEvent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link MockEconomicEventProvider} 단위 테스트.
 *
 * <p>목형 어댑터는 startDate 기준 고정 오프셋 이벤트를 만들고
 * [startDate, startDate+days] 구간으로만 잘라 반환해야 한다.
 */
class MockEconomicEventProviderTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 5);

    private final MockEconomicEventProvider provider = new MockEconomicEventProvider();

    @Test
    void fetchUpcoming_returns_all_sample_events_within_90_days() {
        List<EconomicEvent> events = provider.fetchUpcoming(START, 90);

        assertThat(events).extracting(EconomicEvent::date).containsExactly(
            START.plusDays(3),
            START.plusDays(5),
            START.plusDays(10),
            START.plusDays(12),
            START.plusDays(15),
            START.plusDays(20),
            START.plusDays(25),
            START.plusDays(30)
        );
        assertThat(events.get(0))
            .isEqualTo(new EconomicEvent(START.plusDays(3), "Federal Funds Rate Decision", "USD", "High"));
        assertThat(events).extracting(EconomicEvent::currencyCode)
            .containsExactly("USD", "USD", "USD", "USD", "JPY", "EUR", "USD", "USD");
        assertThat(events).extracting(EconomicEvent::importance)
            .containsExactly("High", "High", "High", "Medium", "High", "High", "Medium", "High");
    }

    @Test
    void fetchUpcoming_filters_out_events_after_the_window_end() {
        List<EconomicEvent> events = provider.fetchUpcoming(START, 12);

        assertThat(events).extracting(EconomicEvent::date).containsExactly(
            START.plusDays(3),
            START.plusDays(5),
            START.plusDays(10),
            START.plusDays(12)
        );
    }

    @Test
    void fetchUpcoming_includes_event_exactly_on_the_window_end() {
        List<EconomicEvent> events = provider.fetchUpcoming(START, 3);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).date()).isEqualTo(START.plusDays(3));
        assertThat(events.get(0).title()).isEqualTo("Federal Funds Rate Decision");
    }

    @Test
    void fetchUpcoming_returns_empty_when_window_ends_before_first_event() {
        assertThat(provider.fetchUpcoming(START, 1)).isEmpty();
    }

    @Test
    void null_start_date_throws() {
        assertThatThrownBy(() -> provider.fetchUpcoming(null, 30))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("startDate");
    }

    @Test
    void non_positive_days_throws() {
        assertThatThrownBy(() -> provider.fetchUpcoming(START, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("days must be positive");
        assertThatThrownBy(() -> provider.fetchUpcoming(START, -5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("days must be positive");
    }
}
