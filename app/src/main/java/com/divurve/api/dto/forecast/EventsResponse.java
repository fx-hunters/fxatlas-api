package com.divurve.api.dto.forecast;

import java.util.List;

/** 경제 일정 응답 (GET /events). */
public record EventsResponse(List<Event> events) {

    /** 경제 이벤트. */
    public record Event(
            String date,
            String title,
            String currencyCode,
            String importance) {
    }
}
