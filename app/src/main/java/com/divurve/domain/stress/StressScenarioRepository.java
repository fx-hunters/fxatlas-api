package com.divurve.domain.stress;

import com.divurve.domain.stress.entity.StressScenario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스트레스 시나리오 마스터 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 시나리오는 시드 데이터(ERD 구축 순서 1단계)라 사용자별 필터가 없다.
 */
public interface StressScenarioRepository extends JpaRepository<StressScenario, String> {

    /** 화면 노출 순서({@code sort_order})대로 조회한다. 순서는 서버가 정하고 클라이언트는 재정렬하지 않는다(NFR-UI-01). */
    List<StressScenario> findAllByOrderBySortOrderAsc();
}
