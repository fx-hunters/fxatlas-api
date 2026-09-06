package com.divurve.api.dto.forecast;

import com.divurve.domain.forecast.ForecastService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 경제 일정 응답 ({@code GET /events}, 명세 v2 §3 — 우선순위 S).
 *
 * <p>일정은 <b>사실</b>이며 방향 전망이 아니다. 어떤 이벤트가 환율을 어느 쪽으로 움직인다는 표현을
 * 담지 않는다(요구사항 §2.2).
 */
@Schema(description = "향후 90일 경제 일정. 방향 전망이 아니라 사실 정보다.")
public record EventsResponse(
        @Schema(description = "일정 목록. 없으면 빈 배열.") List<Event> events) {

    /** 도메인 뷰를 응답 DTO 로 옮긴다. */
    public static EventsResponse from(List<ForecastService.EconomicEventView> views) {
        return new EventsResponse(views.stream()
                .map(view -> new Event(view.date(), view.title(), view.currencyCode(), view.importance()))
                .toList());
    }

    /**
     * 경제 이벤트.
     *
     * @param date         일자
     * @param title        제목
     * @param currencyCode 관련 통화
     * @param importance   중요도
     */
    @Schema(description = "경제 이벤트")
    public record Event(LocalDate date, String title, String currencyCode, String importance) {
    }
}
