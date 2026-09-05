package com.divurve.domain.connectivity;

import com.divurve.domain.connectivity.entity.ConnectivityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 테스트 테이블 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 자체 로직이 없는 인터페이스이므로 별도 @PersistenceAdapter 구현체는 두지 않는다.
 */
public interface ConnectivityCheckRepository extends JpaRepository<ConnectivityCheck, Long> {
}
