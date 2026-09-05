package com.divurve.domain.holding;

import com.divurve.domain.holding.entity.Holding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보유 종목 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 소유자 기준 필터(NFR-SE-03)를 파생 쿼리로 노출한다.
 */
public interface HoldingRepository extends JpaRepository<Holding, UUID> {

    /** 소유자의 보유 종목만 조회한다 (NFR-SE-03). */
    List<Holding> findByOwner_Id(UUID ownerId);
}
