package com.divurve.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.event.entity.EconEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code V14__econ_events.sql} 마이그레이션이 실제로 적용되고, 엔티티 매핑·CHECK/UNIQUE 제약이
 * 의도대로 동작하는지 실제 Postgres(Testcontainers)로 검증한다(이슈 #74).
 */
class EconEventSchemaConstraintTest extends RepositoryTestBase {

    @Autowired
    private EconEventRepository econEventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void extracted_로_생성한_이벤트를_저장하고_조회할_수_있다() {
        EconEvent saved = econEventRepository.save(EconEvent.extracted(
            LocalDate.of(2026, 3, 20), "US", "FOMC 금리 발표", (short) 3,
            "https://example.com/fomc", Instant.parse("2026-03-01T00:00:00Z")));

        EconEvent found = econEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(found.getRegion()).isEqualTo("US");
        assertThat(found.getTitle()).isEqualTo("FOMC 금리 발표");
        assertThat(found.getImpact()).isEqualTo((short) 3);
        assertThat(found.getSourceUrl()).isEqualTo("https://example.com/fomc");
        assertThat(found.getFetchedAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(found.getSourceKind()).isEqualTo(EconEventSourceKind.AI_EXTRACTED.name());
    }

    @Test
    void existsByEventDateAndRegionAndTitle_는_중복_적재_여부를_확인한다() {
        LocalDate eventDate = LocalDate.of(2026, 4, 1);
        econEventRepository.save(EconEvent.extracted(
            eventDate, "KR", "한국은행 금통위", (short) 2, null, Instant.now()));

        assertThat(econEventRepository.existsByEventDateAndRegionAndTitle(eventDate, "KR", "한국은행 금통위"))
            .isTrue();
        assertThat(econEventRepository.existsByEventDateAndRegionAndTitle(eventDate, "KR", "다른 일정"))
            .isFalse();
    }

    @Test
    void 같은_사건이_중복_적재되면_UNIQUE_제약을_위반한다() {
        LocalDate eventDate = LocalDate.of(2026, 5, 5);
        econEventRepository.saveAndFlush(EconEvent.extracted(
            eventDate, "EU", "ECB 통화정책회의", (short) 2, null, Instant.now()));

        assertThatThrownBy(() -> econEventRepository.saveAndFlush(EconEvent.extracted(
            eventDate, "EU", "ECB 통화정책회의", (short) 1, null, Instant.now())))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impact가_범위를_벗어나면_CHECK_제약을_위반한다() {
        EconEvent outOfRange = EconEvent.extracted(
            LocalDate.of(2026, 6, 1), "JP", "일본은행 정책결정회의", (short) 4, null, Instant.now());

        assertThatThrownBy(() -> econEventRepository.saveAndFlush(outOfRange))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void source_kind가_허용된_값이_아니면_CHECK_제약을_위반한다() {
        // EconEvent 의 공개 API(extracted)는 항상 유효한 AI_EXTRACTED 만 만들 수 있어, DB CHECK 제약
        // 자체를 검증하려면 네이티브 SQL로 우회 삽입한다.
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                "insert into econ_events (id, event_date, region, title, impact, fetched_at, source_kind) "
                    + "values (gen_random_uuid(), :eventDate, :region, :title, :impact, :fetchedAt, :sourceKind)")
                .setParameter("eventDate", LocalDate.of(2026, 7, 1))
                .setParameter("region", "US")
                .setParameter("title", "정체불명 출처")
                .setParameter("impact", (short) 1)
                .setParameter("fetchedAt", Instant.now())
                .setParameter("sourceKind", "UNKNOWN_SOURCE")
                .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }
}
