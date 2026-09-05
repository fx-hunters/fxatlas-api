package com.divurve.domain.user;

import com.divurve.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 자체 로직이 없는 인터페이스이므로 별도 @PersistenceAdapter 구현체는 두지 않는다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** 이메일로 사용자를 조회한다. */
    Optional<User> findByEmail(String email);
}
