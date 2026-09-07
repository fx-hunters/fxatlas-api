package com.divurve.domain.plan;

import com.divurve.domain.plan.entity.PlanStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계획 회차 접근 리포지토리. Spring Data JPA 가 런타임 구현을 주입한다.
 * 계획 기준으로 회차를 seq 순서로 조회한다.
 */
public interface PlanStepRepository extends JpaRepository<PlanStep, UUID> {

    /** 계획의 회차를 seq 오름차순으로 조회한다. */
    List<PlanStep> findByPlan_IdOrderBySeqAsc(UUID planId);

    /** 계획의 특정 회차를 조회한다. */
    Optional<PlanStep> findByPlan_IdAndSeq(UUID planId, int seq);

    /**
     * 멱등 키로 이미 반영된 완료 기록을 찾는다 (명세 §14·§21-12).
     *
     * <p>같은 키의 재요청은 저장하지 않고 이 결과를 그대로 돌려준다 — 네트워크 재시도로
     * 남은 금액이 두 번 줄어드는 것을 막는다.
     */
    Optional<PlanStep> findByExecutionKey(String executionKey);
}
