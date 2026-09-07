package com.divurve.domain.event;

import com.divurve.domain.event.entity.EconEvent;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 경제 일정 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * (event_date, region, title) 중복 적재 방지 확인용 존재 여부 조회를 노출한다(이슈 #74).
 */
public interface EconEventRepository extends JpaRepository<EconEvent, UUID> {

    /** 같은 사건이 이미 적재되어 있는지 확인한다 (ERD {@code uq_events_date_region_title}). */
    boolean existsByEventDateAndRegionAndTitle(LocalDate eventDate, String region, String title);
}
