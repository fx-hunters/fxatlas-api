package com.divurve.engine.planner;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * 회차 주기 (플래너 명세 §5.2 {@code preferredCadence} · §5.3 {@code recurInterval}).
 *
 * <p>월 주기를 "30일"로 근사하지 않고 {@link LocalDate#plusMonths} 로 다룬다 — 30일 근사는
 * 회차가 매달 조금씩 앞당겨져 12회차쯤에서 한 주 가까이 어긋난다.
 */
public enum Cadence {

    /** 매주. */
    WEEKLY,

    /** 격주. */
    BIWEEKLY,

    /** 매월 — 같은 일자로 이동하며, 말일은 해당 월의 마지막 날로 맞춰진다. */
    MONTHLY;

    /**
     * 명세의 주기 코드를 enum 으로 바꾼다. 대소문자를 가리지 않는다 — 명세는 소문자
     * ({@code weekly}) 로 적지만 {@code goals.recur_interval} 에는 대문자로 저장돼 있다.
     *
     * @param code 주기 코드
     * @return 대응하는 Cadence
     * @throws IllegalArgumentException 알 수 없는 코드인 경우
     */
    public static Cadence from(String code) {
        Objects.requireNonNull(code, "code");
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "WEEKLY" -> WEEKLY;
            case "BIWEEKLY" -> BIWEEKLY;
            case "MONTHLY" -> MONTHLY;
            default -> throw new IllegalArgumentException("알 수 없는 회차 주기: " + code);
        };
    }

    /**
     * 시작일에서 {@code steps} 주기만큼 진행한 날짜.
     *
     * @param from  시작일
     * @param steps 진행할 주기 수 (0 이면 시작일 그대로)
     * @return 진행한 날짜
     * @throws IllegalArgumentException steps 가 음수인 경우
     */
    public LocalDate advance(LocalDate from, int steps) {
        Objects.requireNonNull(from, "from");
        if (steps < 0) {
            throw new IllegalArgumentException("주기 진행 수는 0 이상이어야 합니다: " + steps);
        }

        return switch (this) {
            case WEEKLY -> from.plusWeeks(steps);
            case BIWEEKLY -> from.plusWeeks(2L * steps);
            case MONTHLY -> from.plusMonths(steps);
        };
    }
}
