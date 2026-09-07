package com.divurve.domain.plan.entity;

import com.divurve.domain.plan.PlanStepStatus;
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

    /** 정기형 회차의 원화 예산 — 정기형은 외화 금액이 아니라 예산이 고정된다 (명세 §10.3). */
    @Column(name = "budget_krw")
    private Long budgetKrw;

    /** 계획 시점의 기준 환율 (외화 1단위당 원화). 회차별 비용 범위의 근거다 (명세 §11.4). */
    @Column(name = "base_rate")
    private Double baseRate;

    @Column(name = "low_cost_krw")
    private Long lowCostKrw;

    @Column(name = "high_cost_krw")
    private Long highCostKrw;

    /** 실제 적용된 환율 (명세 §14). */
    @Column(name = "executed_rate")
    private Double executedRate;

    /** 실제 실행 날짜 (명세 §14). */
    @Column(name = "executed_date")
    private LocalDate executedDate;

    /**
     * 완료 요청의 멱등 키 (명세 §14·§21-12).
     * 같은 키로 재전송된 완료 요청은 두 번 반영되지 않는다.
     */
    @Column(name = "execution_key")
    private String executionKey;

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

    public Long getBudgetKrw() {
        return budgetKrw;
    }

    public Double getBaseRate() {
        return baseRate;
    }

    public Long getLowCostKrw() {
        return lowCostKrw;
    }

    public Long getHighCostKrw() {
        return highCostKrw;
    }

    public Double getExecutedRate() {
        return executedRate;
    }

    public LocalDate getExecutedDate() {
        return executedDate;
    }

    public String getExecutionKey() {
        return executionKey;
    }

    /**
     * 계획 시점의 회차별 비용 근거를 기록한다 (명세 §11.4).
     *
     * @param budgetKrw    정기형 회차 예산 (마감형은 null)
     * @param baseRate     기준 환율 (외화 1단위당 원화)
     * @param lowCostKrw   환율 하단 기준 비용
     * @param highCostKrw  환율 상단 기준 비용
     */
    public void recordCostBasis(Long budgetKrw, Double baseRate, Long lowCostKrw, Long highCostKrw) {
        this.budgetKrw = budgetKrw;
        this.baseRate = baseRate;
        this.lowCostKrw = lowCostKrw;
        this.highCostKrw = highCostKrw;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 회차 완료 기록. 미실행(scheduled·due) 회차만 완료로 전이할 수 있다 — 이미
     * completed/skipped 인 회차를 다시 완료 처리하면 executed_amount 가 덮어써지거나 skipped
     * 이력이 사라지므로 막는다.
     *
     * @param executedAmount 실제 실행한 외화 금액
     * @throws IllegalStateException 이미 완료·건너뛴 회차인 경우
     */
    public void markAsCompleted(double executedAmount) {
        requireStatusIsOpen("완료");
        this.executedAmount = executedAmount;
        this.status = PlanStepStatus.COMPLETED;
    }

    /**
     * 실행 결과를 함께 기록하며 회차를 완료한다 (명세 §14).
     *
     * @param executedAmount 실제 확보한 외화 금액
     * @param executedRate   실제 적용된 환율
     * @param executedDate   실행 날짜
     * @param executionKey   멱등 키. 같은 키의 재요청은 두 번 반영되지 않는다 (§21-12)
     * @throws IllegalStateException 이미 완료·건너뛴 회차인 경우
     */
    public void markAsCompleted(
            double executedAmount, Double executedRate, LocalDate executedDate, String executionKey) {
        markAsCompleted(executedAmount);
        this.executedRate = executedRate;
        this.executedDate = executedDate;
        this.executionKey = executionKey;
    }

    /**
     * 회차 건너뛰기 표시. 미실행(scheduled·due) 회차만 건너뛸 수 있다 — 이미 completed 인 회차를
     * 건너뛰면 실행 기록이 사라지고, 이미 skipped 인 회차를 다시 건너뛰면 부담 재분배가
     * 중복 적용된다.
     *
     * @throws IllegalStateException 이미 완료·건너뛴 회차인 경우
     */
    public void markAsSkipped() {
        requireStatusIsOpen("건너뛰기");
        this.status = PlanStepStatus.SKIPPED;
    }

    /** 예정일이 도래했음을 표시한다 (명세 §13.2 {@code SCHEDULED → DUE}). */
    public void markAsDue() {
        requireStatusIsOpen("도래");
        this.status = PlanStepStatus.DUE;
    }

    private void requireStatusIsOpen(String action) {
        if (!isOpen()) {
            throw new IllegalStateException(
                    "이미 " + status + " 상태인 회차는 " + action + " 처리할 수 없습니다: seq=" + seq);
        }
    }

    /**
     * 회차 금액 업데이트 (건너뛰기로 인한 부담 재분배).
     *
     * @param newAmount 새로운 회차 부담 금액
     */
    public void updateAmount(double newAmount) {
        this.amount = newAmount;
    }

    /**
     * 아직 실행되지 않은 회차인지 확인. 완료·건너뛰기의 출발점이다.
     *
     * @return scheduled 또는 due 상태면 true
     */
    public boolean isOpen() {
        return PlanStepStatus.SCHEDULED.equals(status) || PlanStepStatus.DUE.equals(status);
    }

    /**
     * 회차가 완료 상태인지 확인.
     *
     * @return completed 상태면 true
     */
    public boolean isCompleted() {
        return PlanStepStatus.COMPLETED.equals(status);
    }

    /**
     * 회차가 건너뛴 상태인지 확인.
     *
     * @return skipped 상태면 true
     */
    public boolean isSkipped() {
        return PlanStepStatus.SKIPPED.equals(status);
    }
}
