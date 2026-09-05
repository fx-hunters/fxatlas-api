package com.divurve.domain.plan;

import com.divurve.domain.plan.entity.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계획 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 목표 기준으로 계획 버전을 조회한다.
 */
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /** 목표의 모든 계획 버전을 조회한다. */
    List<Plan> findByGoal_Id(UUID goalId);

    /** 목표의 활성 계획을 조회한다 (활성 버전은 하나만 유지된다). */
    Optional<Plan> findByGoal_IdAndIsActiveTrue(UUID goalId);
}
