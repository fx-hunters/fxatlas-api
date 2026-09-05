package com.divurve.domain.settings;

import com.divurve.domain.settings.entity.RiskProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 성향 프로필 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 사용자당 하나이므로 소유자 기준 단건 조회를 노출한다 (NFR-SE-03).
 */
public interface RiskProfileRepository extends JpaRepository<RiskProfile, UUID> {

    /** 소유자의 성향 프로필을 조회한다. */
    Optional<RiskProfile> findByOwner_Id(UUID ownerId);
}
