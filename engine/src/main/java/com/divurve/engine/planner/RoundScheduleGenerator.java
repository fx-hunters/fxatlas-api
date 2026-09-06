package com.divurve.engine.planner;

import com.divurve.engine.EngineComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 회차 날짜 생성 (플래너 명세 §9.4 마감형 · §10.1 정기형).
 *
 * <p>불변조건 §21-4 — <b>모든 회차는 계획 종료일 이전이다.</b> 종료일 당일은 포함한다.
 * 마감형의 종료일은 {@code targetDate} 에서 영업일 버퍼를 뺀 날이므로, 그날 환전해도
 * 목표일까지 여유가 남는다.
 */
@EngineComponent
public class RoundScheduleGenerator {

    /**
     * 시작일부터 종료일까지 주기별 회차 날짜를 만든다 (명세 §9.4·§10.1).
     *
     * <p>종료일을 넘는 날짜는 만들지 않는다. 시작일이 이미 종료일을 넘었다면 빈 목록이며,
     * 호출부는 이를 "회차를 하나도 만들 수 없는" 검증 실패로 다뤄야 한다 (명세 §8).
     *
     * @param startDate 첫 회차 예정일
     * @param endDate   계획 종료일 (포함)
     * @param cadence   회차 주기
     * @return 오름차순 회차 날짜. 전부 endDate 이하다
     */
    public List<LocalDate> generate(LocalDate startDate, LocalDate endDate, Cadence cadence) {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(cadence, "cadence");

        List<LocalDate> dates = new ArrayList<>();
        for (int step = 0; ; step++) {
            LocalDate date = cadence.advance(startDate, step);
            if (date.isAfter(endDate)) {
                break;
            }
            dates.add(date);
        }
        return dates;
    }

    /**
     * 정기형 점검 기간까지의 회차 날짜 (명세 §10.1).
     *
     * <p>{@code reviewEndDate = startDate + reviewHorizonMonths} 이며, 정기형 Curve 의 마지막
     * 노드는 목표 달성이 아니라 <b>다음 점검</b>이다 (명세 §10.3).
     *
     * @param startDate            첫 계획 시작일
     * @param reviewHorizonMonths  점검 기간 (개월, 1 이상)
     * @param cadence              반복 주기
     * @return 오름차순 회차 날짜
     * @throws IllegalArgumentException 점검 기간이 1개월 미만인 경우
     */
    public List<LocalDate> generateForHorizon(LocalDate startDate, int reviewHorizonMonths, Cadence cadence) {
        Objects.requireNonNull(startDate, "startDate");
        if (reviewHorizonMonths < 1) {
            throw new IllegalArgumentException("점검 기간은 1개월 이상이어야 합니다: " + reviewHorizonMonths);
        }
        return generate(startDate, startDate.plusMonths(reviewHorizonMonths), cadence);
    }
}
