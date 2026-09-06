package com.divurve.domain.plan.entity;

import com.divurve.domain.goal.entity.Goal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 계획을 비활성화한다 (새 버전 생성 시 이전 버전 비활성화용).
     */
    public void deactivate() {
        this.isActive = false;
    }
}
