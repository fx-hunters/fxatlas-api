package com.divurve.domain.event;

import com.divurve.domain.port.EconEventExtractor.ExtractedEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 추출된 이벤트 후보 1건을 검증하는 순수 로직 (이슈 #74). Spring 의존이 없다 — {@code engine} 과 같은
 * 이유로, 결정론적 규칙은 프레임워크 없이도 독립적으로 테스트돼야 한다.
 *
 * <p>거부 사유는 호출자(《{@link EconEventIngestionService}》)에게 그대로 남는다(감사·로그용) —
 * {@link Result#rejectReason()} 이 그 역할을 한다.
 */
public final class EconEventValidator {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int IMPACT_MIN = 1;
    private static final int IMPACT_MAX = 3;
    private static final Set<String> ALLOWED_REGIONS =
            Set.of("US", "EU", "JP", "KR", "CN", "GB", "GLOBAL");

    private static final String REASON_TITLE_BLANK = "title_blank";
    private static final String REASON_TITLE_TOO_LONG = "title_too_long";
    private static final String REASON_REGION_NOT_ALLOWED = "region_not_allowed";
    private static final String REASON_IMPACT_OUT_OF_RANGE = "impact_out_of_range";
    private static final String REASON_EVENT_DATE_UNPARSEABLE = "event_date_unparseable";
    private static final String REASON_EVENT_DATE_NOT_FOUND_IN_SOURCE = "event_date_not_found_in_source";

    /**
     * 후보 1건을 검증한다.
     *
     * @param candidate  추출기가 돌려준 원시 후보
     * @param sourceText 원문 전문 — {@code eventDate} 가 실제로 등장하는지 대조하는 근거
     * @return 통과하면 정규화된 값을, 실패하면 사유를 담은 결과
     */
    public Result validate(ExtractedEvent candidate, String sourceText) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(sourceText, "sourceText");

        Optional<String> rejection = firstRejectionReason(candidate, sourceText);
        if (rejection.isPresent()) {
            return new Result(false, null, rejection.get());
        }
        return new Result(true, normalize(candidate), null);
    }

    private Optional<String> firstRejectionReason(ExtractedEvent candidate, String sourceText) {
        return validateTitle(candidate.title())
                .or(() -> validateRegion(candidate.region()))
                .or(() -> validateImpact(candidate.impact()))
                .or(() -> validateEventDate(candidate.eventDate(), sourceText));
    }

    private Optional<String> validateTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.of(REASON_TITLE_BLANK);
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            return Optional.of(REASON_TITLE_TOO_LONG);
        }
        return Optional.empty();
    }

    private Optional<String> validateRegion(String region) {
        if (region == null || !ALLOWED_REGIONS.contains(region.trim().toUpperCase(Locale.ROOT))) {
            return Optional.of(REASON_REGION_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    private Optional<String> validateImpact(Integer impact) {
        if (impact == null || impact < IMPACT_MIN || impact > IMPACT_MAX) {
            return Optional.of(REASON_IMPACT_OUT_OF_RANGE);
        }
        return Optional.empty();
    }

    private Optional<String> validateEventDate(String rawEventDate, String sourceText) {
        LocalDate parsed = parseIsoDate(rawEventDate);
        if (parsed == null) {
            return Optional.of(REASON_EVENT_DATE_UNPARSEABLE);
        }
        if (!DateMentionMatcher.appearsIn(parsed, sourceText)) {
            return Optional.of(REASON_EVENT_DATE_NOT_FOUND_IN_SOURCE);
        }
        return Optional.empty();
    }

    private static LocalDate parseIsoDate(String rawEventDate) {
        if (rawEventDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(rawEventDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 모든 필드가 검증을 통과한 뒤에만 호출된다 — 재파싱은 실패하지 않는다. */
    private ValidEvent normalize(ExtractedEvent candidate) {
        LocalDate eventDate = LocalDate.parse(candidate.eventDate().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        String region = candidate.region().trim().toUpperCase(Locale.ROOT);
        String title = candidate.title().trim();
        short impact = candidate.impact().shortValue();
        return new ValidEvent(eventDate, region, title, impact);
    }

    /**
     * 검증 결과. {@code valid} 가 {@code false} 면 {@code value} 는 항상 {@code null} 이고
     * {@code rejectReason} 이 채워진다 — 감사·로그에서 이 사유를 그대로 남긴다.
     */
    public record Result(boolean valid, ValidEvent value, String rejectReason) {
    }

    /** 검증·정규화를 통과한 이벤트. */
    public record ValidEvent(LocalDate eventDate, String region, String title, short impact) {
    }

    /**
     * 원문에 특정 날짜가 실제로 언급되는지 정규식으로 대조한다 (이슈 #74 제약 2 —
     * "미래 날짜를 LLM 이 만들지 않는다"). {@code eventDate} 가 파싱에는 성공해도 원문에 없는 값이면
     * LLM 이 지어낸 것으로 보고 버린다.
     *
     * <p><b>커버하는 표기</b>:
     * <ul>
     *   <li>ISO 스타일 숫자: {@code 2026-09-07}, {@code 2026.09.07}, {@code 2026/09/07}
     *       (월·일 0 패딩 있음/없음 모두)</li>
     *   <li>미국식 슬래시: {@code 09/07/2026} (0 패딩 있음/없음 모두)</li>
     *   <li>한국어: {@code 2026년 9월 7일}, {@code 9월 7일} (연도 생략형), 다중 공백 허용</li>
     *   <li>영어 월 이름: {@code September 7}, {@code Sept 7}, {@code Sep 7}, {@code 7 September}</li>
     * </ul>
     *
     * <p><b>커버하지 못해 포기한 표기</b> — 서수(21st, 3rd), 요일 결합("Monday, September 7"),
     * 상대 표현("다음 주 월요일"), 회계연도/분기 표기(FY26 Q3). 이런 표기만 있는 원문은 매칭에
     * 실패해 해당 행이 버려진다 — 잘못 저장하는 것보다 안전한 실패로 간주한다.
     */
    private static final class DateMentionMatcher {

        private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };
        private static final String[] MONTH_ABBREVIATIONS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        };

        private DateMentionMatcher() {
        }

        static boolean appearsIn(LocalDate date, String sourceText) {
            return candidatePatterns(date).stream()
                    .anyMatch(pattern -> pattern.matcher(sourceText).find());
        }

        private static List<Pattern> candidatePatterns(LocalDate date) {
            int year = date.getYear();
            int monthValue = date.getMonthValue();
            int dayValue = date.getDayOfMonth();
            String month = optionalZeroPad(monthValue);
            String day = optionalZeroPad(dayValue);
            String monthName = MONTH_NAMES[monthValue - 1];
            String monthAbbreviation = MONTH_ABBREVIATIONS[monthValue - 1];

            List<Pattern> patterns = new ArrayList<>();
            patterns.add(compile("\\b" + year + "[-./]" + month + "[-./]" + day + "\\b"));
            patterns.add(compile("\\b" + month + "/" + day + "/" + year + "\\b"));
            patterns.add(compile("\\b" + year + "\\s*년\\s*" + monthValue + "\\s*월\\s*" + dayValue + "\\s*일"));
            patterns.add(compile(monthValue + "\\s*월\\s*" + dayValue + "\\s*일"));
            patterns.add(compile(monthName + "\\.?\\s+" + dayValue + "\\b"));
            patterns.add(compile(monthAbbreviation + "t?\\.?\\s+" + dayValue + "\\b"));
            patterns.add(compile("\\b" + dayValue + "\\s+" + monthName));
            return patterns;
        }

        private static String optionalZeroPad(int value) {
            return value < 10 ? "0?" + value : String.valueOf(value);
        }

        private static Pattern compile(String regex) {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
    }
}
