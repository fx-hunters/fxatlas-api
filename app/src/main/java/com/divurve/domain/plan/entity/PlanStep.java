package com.divurve.domain.plan.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 계획(Plan)에 속한 환전 회차. seq 순서로 조회된다.
 * executed_amount 는 회차 실행 시 채워지는 실집행 금액이다.
 */
@Entity
@Table(name = "plan_steps")
public class PlanStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private int seq;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private double amount;

    @Column(name = "executed_amount", nullable = false)
    private double executedAmount;

    @Column(nullable = false)
    private String status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected PlanStep() {
    }

    private PlanStep(Plan plan, int seq, LocalDate scheduledDate, double amount, double executedAmount, String status) {
        this.plan = plan;
        this.seq = seq;
        this.scheduledDate = scheduledDate;
        this.amount = amount;
        this.executedAmount = executedAmount;
        this.status = status;
    }

    /** 새 회차를 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static PlanStep create(Plan plan, int seq, LocalDate scheduledDate, double amount, double executedAmount,
            String status) {
        return new PlanStep(plan, seq, scheduledDate, amount, executedAmount, status);
    }

    public UUID getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getSeq() {
        return seq;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public double getAmount() {
        return amount;
    }

    public double getExecutedAmount() {
        return executedAmount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
