package com.divurve.domain.settings;

import com.divurve.domain.settings.entity.UserSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 설정 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 사용자당 하나이므로 소유자 기준 단건 조회를 노출한다 (NFR-SE-03).
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    /** 소유자의 설정을 조회한다. */
    Optional<UserSettings> findByOwner_Id(UUID ownerId);
}
