package com.divurve.domain.plan;

import com.divurve.domain.plan.entity.PlanStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계획 회차 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 계획 기준으로 회차를 seq 순서로 조회한다.
 */
public interface PlanStepRepository extends JpaRepository<PlanStep, UUID> {

    /** 계획의 회차를 seq 오름차순으로 조회한다. */
    List<PlanStep> findByPlan_IdOrderBySeqAsc(UUID planId);
}
