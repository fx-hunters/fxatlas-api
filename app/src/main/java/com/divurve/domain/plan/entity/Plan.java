package com.divurve.domain.plan.entity;

import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 목표(Goal)에 속한 환전 계획. 버전별로 저장되며 활성 버전은 하나만 유지된다.
 * opportunity 는 회차가 아니라 단일 대기 물량으로 저장된다.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(nullable = false)
    private int version;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column
    private String reason;

    @Column(name = "safe_ratio", nullable = false)
    private double safeRatio;

    @Column(name = "split_count", nullable = false)
    private int splitCount;

    @Column(name = "opportunity_amount", nullable = false)
    private double opportunityAmount;

    @Column(name = "opportunity_trigger_rate", nullable = false)
    private double opportunityTriggerRate;

    /**
     * 계획 상태 (플래너 명세 §13.1). {@link #isActive} 를 대체한다 — 여섯 상태를 boolean
     * 하나로 표현할 수 없다. is_active 는 소비자가 남아 있어 이슈 #85 에서 정리한다.
     */
    @Column(nullable = false)
    private String status;

    /** 계획 종료일 {@code targetDate - businessDayBuffer} (명세 §9.4). 정기형은 점검 종료일. */
    @Column(name = "plan_end_date")
    private LocalDate planEndDate;

    @Embedded
    private PlanCalculationMeta calculationMeta;

    @Embedded
    private PlanCostSummary costSummary;

    /** 이 계획을 대체한 새 버전 (명세 §18). 자기 참조이므로 id 만 들고 순환 로딩을 피한다. */
    @Column(name = "superseded_by", columnDefinition = "uuid")
    private UUID supersededBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected Plan() {
    }

    private Plan(Builder builder) {
        this.goal = builder.goal;
        this.version = builder.version;
        this.isActive = builder.isActive;
        this.reason = builder.reason;
        this.safeRatio = builder.safeRatio;
        this.splitCount = builder.splitCount;
        this.opportunityAmount = builder.opportunityAmount;
        this.opportunityTriggerRate = builder.opportunityTriggerRate;
        this.status = builder.status;
        this.planEndDate = builder.planEndDate;
        this.calculationMeta = builder.calculationMeta;
        this.costSummary = builder.costSummary;
    }

    /** 필드 수가 많아 빌더로 생성한다. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static Builder builder(Goal goal, int version) {
        return new Builder(goal, version);
    }

    /** Plan 생성용 빌더. */
    public static final class Builder {
        private final Goal goal;
        private final int version;
        private boolean isActive;
        private String reason;
        private double safeRatio;
        private int splitCount;
        private double opportunityAmount;
        private double opportunityTriggerRate;
        private String status = PlanStatus.DRAFT;
        private LocalDate planEndDate;
        private PlanCalculationMeta calculationMeta;
        private PlanCostSummary costSummary;

        private Builder(Goal goal, int version) {
            this.goal = goal;
            this.version = version;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder safeRatio(double safeRatio) {
            this.safeRatio = safeRatio;
            return this;
        }

        public Builder splitCount(int splitCount) {
            this.splitCount = splitCount;
            return this;
        }

        public Builder opportunityAmount(double opportunityAmount) {
            this.opportunityAmount = opportunityAmount;
            return this;
        }

        public Builder opportunityTriggerRate(double opportunityTriggerRate) {
            this.opportunityTriggerRate = opportunityTriggerRate;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder planEndDate(LocalDate planEndDate) {
            this.planEndDate = planEndDate;
            return this;
        }

        public Builder calculationMeta(PlanCalculationMeta calculationMeta) {
            this.calculationMeta = calculationMeta;
            return this;
        }

        public Builder costSummary(PlanCostSummary costSummary) {
            this.costSummary = costSummary;
            return this;
        }

        public Plan build() {
            return new Plan(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public Goal getGoal() {
        return goal;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getReason() {
        return reason;
    }

    public double getSafeRatio() {
        return safeRatio;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public double getOpportunityAmount() {
        return opportunityAmount;
    }

    public double getOpportunityTriggerRate() {
        return opportunityTriggerRate;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPlanEndDate() {
        return planEndDate;
    }

    public PlanCalculationMeta getCalculationMeta() {
        return calculationMeta;
    }

    public PlanCostSummary getCostSummary() {
        return costSummary;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 계획을 비활성화한다 (새 버전 생성 시 이전 버전 비활성화용).
     *
     * <p>{@code is_active} 와 {@code status} 를 함께 옮긴다 — 두 컬럼이 공존하는 동안
     * 한쪽만 바꾸면 부분 유니크 인덱스({@code status = 'active'})와 기존 조회
     * ({@code findByGoal_IdAndIsActiveTrue})가 서로 다른 계획을 가리키게 된다.
     * is_active 가 제거되면(이슈 #85) 이 메서드는 상태 전이 하나만 남는다.
     */
    public void deactivate() {
        this.isActive = false;
        this.status = PlanStatus.SUPERSEDED;
    }

    /**
     * 새 버전으로 대체됐음을 기록한다 (명세 §18).
     *
     * @param newPlanId 이 계획을 대체한 계획의 ID
     */
    public void supersededBy(UUID newPlanId) {
        this.supersededBy = newPlanId;
    }

    /** 현재 적용 중인 계획인지 (명세 §13.1). */
    public boolean isActivePlan() {
        return PlanStatus.ACTIVE.equals(status);
    }
}
