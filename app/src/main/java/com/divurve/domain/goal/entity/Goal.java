package com.divurve.domain.goal.entity;

import com.divurve.domain.user.entity.User;
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
 * 외화 목표. 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 * held_amount/suggested 등 계산값은 저장하지 않고 이후 engine·deposits 조회로 산출한다.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "target_amount", nullable = false)
    private double targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "recur_interval")
    private String recurInterval;

    @Column(name = "budget_amount", nullable = false)
    private long budgetAmount;

    @Column(name = "budget_currency_code")
    private String budgetCurrencyCode;

    @Column(name = "budget_period")
    private String budgetPeriod;

    @Column(name = "is_speculative", nullable = false)
    private boolean isSpeculative;

    @Column(nullable = false)
    private String status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected Goal() {
    }

    private Goal(Builder builder) {
        this.owner = builder.owner;
        this.name = builder.name;
        this.kind = builder.kind;
        this.purpose = builder.purpose;
        this.currencyCode = builder.currencyCode;
        this.targetAmount = builder.targetAmount;
        this.targetDate = builder.targetDate;
        this.recurInterval = builder.recurInterval;
        this.budgetAmount = builder.budgetAmount;
        this.budgetCurrencyCode = builder.budgetCurrencyCode;
        this.budgetPeriod = builder.budgetPeriod;
        this.isSpeculative = builder.isSpeculative;
        this.status = builder.status;
    }

    /** 필드 수가 많아 빌더로 생성한다. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static Builder builder(User owner, String name, String kind, String purpose, String currencyCode) {
        return new Builder(owner, name, kind, purpose, currencyCode);
    }

    /** Goal 생성용 빌더. 필수값은 {@link #builder} 인자로, 선택값은 체이닝으로 지정한다. */
    public static final class Builder {
        private final User owner;
        private final String name;
        private final String kind;
        private final String purpose;
        private final String currencyCode;
        private double targetAmount;
        private LocalDate targetDate;
        private String recurInterval;
        private long budgetAmount;
        private String budgetCurrencyCode;
        private String budgetPeriod;
        private boolean isSpeculative;
        private String status;

        private Builder(User owner, String name, String kind, String purpose, String currencyCode) {
            this.owner = owner;
            this.name = name;
            this.kind = kind;
            this.purpose = purpose;
            this.currencyCode = currencyCode;
        }

        public Builder targetAmount(double targetAmount) {
            this.targetAmount = targetAmount;
            return this;
        }

        public Builder targetDate(LocalDate targetDate) {
            this.targetDate = targetDate;
            return this;
        }

        public Builder recurInterval(String recurInterval) {
            this.recurInterval = recurInterval;
            return this;
        }

        public Builder budgetAmount(long budgetAmount) {
            this.budgetAmount = budgetAmount;
            return this;
        }

        public Builder budgetCurrencyCode(String budgetCurrencyCode) {
            this.budgetCurrencyCode = budgetCurrencyCode;
            return this;
        }

        public Builder budgetPeriod(String budgetPeriod) {
            this.budgetPeriod = budgetPeriod;
            return this;
        }

        public Builder isSpeculative(boolean isSpeculative) {
            this.isSpeculative = isSpeculative;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Goal build() {
            return new Goal(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public double getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getRecurInterval() {
        return recurInterval;
    }

    public long getBudgetAmount() {
        return budgetAmount;
    }

    public String getBudgetCurrencyCode() {
        return budgetCurrencyCode;
    }

    public String getBudgetPeriod() {
        return budgetPeriod;
    }

    public boolean isSpeculative() {
        return isSpeculative;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
