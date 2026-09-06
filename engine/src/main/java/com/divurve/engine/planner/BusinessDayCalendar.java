package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 영업일 계산 (플래너 명세 §9.4·§21-5).
 *
 * <p><b>현재는 주말만 제외한다.</b> 명세 §7 은 "한국·대상 시장 영업일 캘린더"를 요구하지만
 * 공휴일 테이블이 아직 없다. 공휴일이 도입되면 이 클래스에 주입해 {@link #isBusinessDay} 만
 * 확장하면 되고, 호출부는 바뀌지 않는다 — 그래서 static 유틸이 아니라 빈으로 둔다.
 * 공휴일을 넣는 순간 회차 날짜와 마감 버퍼가 달라지므로 커밋 타입은 {@code calc} 다.
 */
@EngineComponent
public class BusinessDayCalendar {

    /**
     * 영업일인지 판정한다. 현재 기준은 주중 여부뿐이다.
     *
     * @param date 판정할 날짜
     * @return 토·일이 아니면 true
     */
    public boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date");
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 기준일에서 영업일 기준으로 {@code days} 일 앞선 날짜 (명세 §9.4 마감 버퍼).
     *
     * <p>기준일 자체가 영업일인지는 세지 않는다 — {@code days} 만큼의 영업일을 실제로 거슬러
     * 올라간다. {@code days=0} 이면 기준일을 그대로 돌려준다.
     *
     * @param date 기준일
     * @param days 거슬러 올라갈 영업일 수 (0 이상)
     * @return 영업일 기준으로 days 일 앞선 날짜 (항상 영업일)
     * @throws IllegalArgumentException days 가 음수인 경우
     */
    public LocalDate minusBusinessDays(LocalDate date, int days) {
        Objects.requireNonNull(date, "date");
        if (days < 0) {
            throw new IllegalArgumentException("영업일 수는 0 이상이어야 합니다: " + days);
        }

        LocalDate cursor = date;
        int remaining = days;
        while (remaining > 0) {
            cursor = cursor.minusDays(1);
            if (isBusinessDay(cursor)) {
                remaining--;
            }
        }
        return cursor;
    }
}
