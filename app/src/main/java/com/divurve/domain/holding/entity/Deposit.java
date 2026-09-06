package com.divurve.domain.holding.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 외화 예금. 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 * 외화 금액은 소수 4자리(명세 1.4)이므로 {@code numeric(19,4)} 로 저장한다.
 */
@Entity
@Table(name = "fx_deposits")
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected Deposit() {
    }

    private Deposit(User owner, String currencyCode, BigDecimal amount) {
        this.owner = owner;
        this.currencyCode = currencyCode;
        this.amount = amount;
    }

    /** 새 외화 예금을 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static Deposit create(User owner, String currencyCode, BigDecimal amount) {
        return new Deposit(owner, currencyCode, amount);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
