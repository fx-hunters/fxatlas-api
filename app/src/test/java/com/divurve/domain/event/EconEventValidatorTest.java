package com.divurve.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.event.EconEventValidator.Result;
import com.divurve.domain.port.EconEventExtractor.ExtractedEvent;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EconEventValidator} 검증 (이슈 #74). 특히 "미래 날짜를 LLM 이 만들지 않는다"(제약 2)를
 * 지키는 원문 대조 로직의 표기 변형 각각을 확인한다.
 */
@DisplayName("EconEventValidator")
class EconEventValidatorTest {

    private final EconEventValidator validator = new EconEventValidator();

    private static ExtractedEvent candidate(String eventDate, String region, String title, Integer impact) {
        return new ExtractedEvent(eventDate, region, title, impact);
    }

    @Test
    @DisplayName("모든 필드가 유효하면 정규화된 값을 돌려준다")
    void validCandidateIsAcceptedAndNormalized() {
        ExtractedEvent candidate = candidate("2026-09-07", "us", "  FOMC 회의  ", 3);

        Result result = validator.validate(candidate, "2026-09-07 FOMC 회의 예정");

        assertThat(result.valid()).isTrue();
        assertThat(result.rejectReason()).isNull();
        assertThat(result.value().eventDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(result.value().region()).isEqualTo("US");
        assertThat(result.value().title()).isEqualTo("FOMC 회의");
        assertThat(result.value().impact()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("title 이 null 이면 거부한다")
    void rejectsNullTitle() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", null, 2), "2026-09-07 발표");

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("title_blank");
    }

    @Test
    @DisplayName("title 이 공백뿐이면 거부한다")
    void rejectsBlankTitle() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "   ", 2), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("title_blank");
    }

    @Test
    @DisplayName("title 이 trim 후 200자를 초과하면 거부한다")
    void rejectsTooLongTitle() {
        String longTitle = " " + "a".repeat(201) + " ";

        Result result = validator.validate(
                candidate("2026-09-07", "US", longTitle, 2), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("title_too_long");
    }

    @Test
    @DisplayName("title 이 trim 후 정확히 200자면 통과한다")
    void acceptsExactlyMaxLengthTitle() {
        String exactTitle = "a".repeat(200);

        Result result = validator.validate(
                candidate("2026-09-07", "US", exactTitle, 2), "2026-09-07 발표");

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("region 이 허용 ENUM 밖이면 거부한다")
    void rejectsUnknownRegion() {
        Result result = validator.validate(
                candidate("2026-09-07", "FR", "제목", 2), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("region_not_allowed");
    }

    @Test
    @DisplayName("region 이 null 이면 거부한다")
    void rejectsNullRegion() {
        Result result = validator.validate(
                candidate("2026-09-07", null, "제목", 2), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("region_not_allowed");
    }

    @Test
    @DisplayName("region 대소문자를 무시하고 대문자로 정규화한다")
    void normalizesRegionCase() {
        Result result = validator.validate(
                candidate("2026-09-07", "kr", "제목", 2), "2026-09-07 발표");

        assertThat(result.value().region()).isEqualTo("KR");
    }

    @Test
    @DisplayName("impact 가 null 이면 거부한다")
    void rejectsNullImpact() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "제목", null), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("impact_out_of_range");
    }

    @Test
    @DisplayName("impact 가 범위 밖(0)이면 거부한다")
    void rejectsImpactBelowRange() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "제목", 0), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("impact_out_of_range");
    }

    @Test
    @DisplayName("impact 가 범위 밖(4)이면 거부한다")
    void rejectsImpactAboveRange() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "제목", 4), "2026-09-07 발표");

        assertThat(result.rejectReason()).isEqualTo("impact_out_of_range");
    }

    @Test
    @DisplayName("eventDate 파싱이 실패하면 거부한다")
    void rejectsUnparseableDate() {
        Result result = validator.validate(
                candidate("내년 어느 날", "US", "제목", 2), "내년 어느 날 발표");

        assertThat(result.rejectReason()).isEqualTo("event_date_unparseable");
    }

    @Test
    @DisplayName("eventDate 가 null 이면 거부한다")
    void rejectsNullEventDate() {
        Result result = validator.validate(
                candidate(null, "US", "제목", 2), "발표 예정");

        assertThat(result.rejectReason()).isEqualTo("event_date_unparseable");
    }

    @Test
    @DisplayName("원문에 날짜가 등장하지 않으면 거부한다 (핵심 제약)")
    void rejectsDateNotMentionedInSource() {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "제목", 2), "구체적인 일정 언급이 없는 기사 본문");

        assertThat(result.rejectReason()).isEqualTo("event_date_not_found_in_source");
    }

    @Test
    @DisplayName("ISO 하이픈 표기(0 패딩)를 인식한다")
    void recognizesIsoHyphenPadded() {
        assertDateAccepted("2026-09-07 발표 예정");
    }

    @Test
    @DisplayName("ISO 점 표기를 인식한다")
    void recognizesIsoDot() {
        assertDateAccepted("2026.09.07 발표 예정");
    }

    @Test
    @DisplayName("ISO 슬래시 표기(0 패딩 없음)를 인식한다")
    void recognizesIsoSlashUnpadded() {
        assertDateAccepted("2026/9/7 발표 예정");
    }

    @Test
    @DisplayName("한국어 연-월-일 전체 표기를 인식한다")
    void recognizesKoreanFullDate() {
        assertDateAccepted("2026년 9월 7일 발표 예정");
    }

    @Test
    @DisplayName("한국어 연도 생략형(월-일)을 인식한다")
    void recognizesKoreanMonthDayOnly() {
        assertDateAccepted("이번 9월 7일 발표 예정");
    }

    @Test
    @DisplayName("한국어 다중 공백 표기를 인식한다")
    void recognizesKoreanExtraSpacing() {
        assertDateAccepted("2026년  9월   7일 발표 예정");
    }

    @Test
    @DisplayName("영어 전체 월 이름 표기를 인식한다")
    void recognizesEnglishFullMonthName() {
        assertDateAccepted("Scheduled for September 7 in the release calendar");
    }

    @Test
    @DisplayName("영어 Sept 축약형을 인식한다")
    void recognizesEnglishSeptAbbreviation() {
        assertDateAccepted("Scheduled for Sept 7 release");
    }

    @Test
    @DisplayName("영어 Sep 축약형을 인식한다")
    void recognizesEnglishSepAbbreviation() {
        assertDateAccepted("Scheduled for Sep 7 release");
    }

    @Test
    @DisplayName("영어 일-월 순서 표기를 인식한다")
    void recognizesEnglishDayThenMonth() {
        assertDateAccepted("Scheduled for 7 September release");
    }

    @Test
    @DisplayName("미국식 월/일/연도 슬래시 표기를 인식한다")
    void recognizesUsSlashOrder() {
        assertDateAccepted("Scheduled for 09/07/2026 release");
    }

    @Test
    @DisplayName("월·일이 두 자리 숫자여도 0 패딩 없이 인식한다")
    void recognizesTwoDigitMonthAndDay() {
        Result result = validator.validate(
                candidate("2026-11-23", "US", "제목", 2), "2026-11-23 발표 예정");

        assertThat(result.valid()).isTrue();
    }

    private void assertDateAccepted(String sourceText) {
        Result result = validator.validate(
                candidate("2026-09-07", "US", "제목", 2), sourceText);

        assertThat(result.valid()).isTrue();
    }
}
