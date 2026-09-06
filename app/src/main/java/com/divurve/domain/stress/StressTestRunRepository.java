package com.divurve.domain.stress;

import com.divurve.domain.stress.entity.StressTestRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스트레스 실행 이력 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 소유자 기준 필터(NFR-SE-02)를 파생 쿼리로 노출한다 — 남의 실행 이력은 조회되지 않는다.
 */
public interface StressTestRunRepository extends JpaRepository<StressTestRun, UUID> {

    /** 소유자의 실행 이력을 최신순으로 조회한다 (ERD {@code idx_stress_user} 와 같은 정렬). */
    List<StressTestRun> findByOwner_IdOrderByCreatedAtDesc(UUID ownerId);
}
