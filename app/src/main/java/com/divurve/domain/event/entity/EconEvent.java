package com.divurve.domain.event.entity;

import com.divurve.domain.event.EconEventSourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 경제 일정 한 건 (ERD {@code econ_events}, 이슈 #74). 공식 파서·AI 추출·시연용 예시
 * 데이터를 {@link #sourceKind} 로 구분해 신뢰도를 섞지 않는다(이슈 #74 제약 4).
 *
 * <p>{@code event_date} 는 원문에 실제로 등장한 날짜여야 한다(이슈 #74 제약 2) — 이 규칙은
 * 추출 단계(포트/유스케이스)의 책임이며, 이 엔티티는 검증을 통과한 값만 받는다.
 */
@Entity
@Table(name = "econ_events")
public class EconEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private short impact;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "source_kind", nullable = false)
    private String sourceKind;

    /** JPA 전용 기본 생성자. */
    protected EconEvent() {
    }

    private EconEvent(LocalDate eventDate, String region, String title, short impact,
            String sourceUrl, Instant fetchedAt, EconEventSourceKind sourceKind) {
        this.eventDate = Objects.requireNonNull(eventDate, "eventDate");
        this.region = Objects.requireNonNull(region, "region");
        this.title = Objects.requireNonNull(title, "title");
        this.impact = impact;
        this.sourceUrl = sourceUrl;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind").name();
    }

    /**
     * 비정형 원문에서 LLM 이 추출한 경제 일정을 생성한다({@link EconEventSourceKind#AI_EXTRACTED}).
     * 원문에 없는 날짜를 만들어내지 않는다는 전제(이슈 #74 제약 2)는 호출자가 검증한 뒤 넘긴다.
     */
    public static EconEvent extracted(LocalDate eventDate, String region, String title,
            short impact, String sourceUrl, Instant fetchedAt) {
        return new EconEvent(eventDate, region, title, impact, sourceUrl, fetchedAt,
            EconEventSourceKind.AI_EXTRACTED);
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public String getRegion() {
        return region;
    }

    public String getTitle() {
        return title;
    }

    public short getImpact() {
        return impact;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getSourceKind() {
        return sourceKind;
    }
}
