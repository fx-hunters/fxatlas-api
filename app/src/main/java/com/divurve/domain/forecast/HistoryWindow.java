package com.divurve.domain.forecast;

/**
 * 필요한 <b>관측 수(영업일)</b>를 외부 어댑터에 요청할 <b>조회 구간(달력일)</b>으로 환산한다 (이슈 #57).
 *
 * <p>환율 종가는 영업일에만 존재하는데, 어댑터의 조회 구간은
 * {@code endDate.minusDays(n)} 즉 달력일로 잘린다. 두 단위를 같은 숫자로 쓰면 요청한 관측의
 * 약 69%(= 252/365)만 돌아온다. 실제로 5년 백분위에 필요한 1,290 영업일을 1,290 달력일로
 * 요청해 약 880개만 받았고, {@code VolatilityCalculator.calculatePercentile5y} 가
 * {@code IllegalArgumentException} 을 던져 {@code /forecast} 가 500 으로 실패했다.
 * {@code /market/regime} 은 더 나빠서, 예외를 삼키고 통화쌍을 통째로 건너뛰어
 * <b>빈 {@code pair_regimes} 를 200 으로</b> 돌려주고 있었다.
 *
 * <p>어댑터의 {@code days} 파라미터는 이제 달력일로 못박혀 있다
 * ({@link com.divurve.domain.port.FxRateHistoryProvider#fetchHistorical}).
 * 도메인은 필요한 영업일 수만 알고, 환산은 이 클래스 한 곳에서만 한다.
 */
public final class HistoryWindow {

    /** 1년의 영업일 수 (금융 관례). */
    public static final int BUSINESS_DAYS_PER_YEAR = 252;

    /** 1년의 달력일 수. */
    public static final int CALENDAR_DAYS_PER_YEAR = 365;

    /**
     * 공휴일·데이터 결측 대비 여유율.
     *
     * <p>252 는 관례값이라 한국 공휴일(연 15일 내외)을 정확히 반영하지 못한다. 환산값이 조금이라도
     * 모자라면 계산 전체가 실패하고, 조금 넉넉하면 응답에서 잘라 쓰면 그만이므로 넉넉한 쪽으로 둔다.
     */
    private static final double SAFETY_MARGIN = 1.10;

    private HistoryWindow() {
        throw new UnsupportedOperationException("HistoryWindow is a utility class");
    }

    /**
     * 영업일 관측 {@code businessDays} 개를 확보하기 위해 거슬러 올라가야 하는 달력일 수.
     *
     * @param businessDays 필요한 영업일 관측 수 (양수)
     * @return 조회 구간 길이(달력일). 항상 {@code businessDays} 보다 크다
     * @throws IllegalArgumentException {@code businessDays} 가 0 이하일 때
     */
    public static int calendarDaysFor(int businessDays) {
        if (businessDays <= 0) {
            throw new IllegalArgumentException(
                    "businessDays must be positive, got %d".formatted(businessDays));
        }
        double ratio = (double) CALENDAR_DAYS_PER_YEAR / BUSINESS_DAYS_PER_YEAR;
        return (int) Math.ceil(businessDays * ratio * SAFETY_MARGIN);
    }
}
