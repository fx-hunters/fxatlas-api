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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 외화 예금. 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 * 외화 금액은 소수 4자리(명세 1.4)이므로 {@code numeric(19,4)} 로 저장한다.
 * 매입 환율(purchaseFxRateKrw)은 FR-ON-04 에 따라 예치 시점 원화 환산 근거로 저장하며,
 * NFR-DT-01 에 따라 출처(source)·기준일(asOf)을 함께 보존한다.
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

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(name = "purchase_fx_rate_krw", precision = 19, scale = 4)
    private BigDecimal purchaseFxRateKrw;

    @Column(name = "purchase_fx_rate_source")
    private String purchaseFxRateSource;

    @Column(name = "purchase_fx_rate_as_of")
    private LocalDate purchaseFxRateAsOf;

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

    /** 매입 환율 컨텍스트(있으면 fx 3필드 모두, 없으면 null)를 붙인다. FR-ON-04. */
    public void assignPurchaseContext(LocalDate purchasedAt, PurchaseFxRate fxRate) {
        this.purchasedAt = purchasedAt;
        if (fxRate == null) {
            this.purchaseFxRateKrw = null;
            this.purchaseFxRateSource = null;
            this.purchaseFxRateAsOf = null;
        } else {
            this.purchaseFxRateKrw = fxRate.rateKrw();
            this.purchaseFxRateSource = fxRate.source();
            this.purchaseFxRateAsOf = fxRate.asOf();
        }
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

    public LocalDate getPurchasedAt() {
        return purchasedAt;
    }

    public BigDecimal getPurchaseFxRateKrw() {
        return purchaseFxRateKrw;
    }

    public String getPurchaseFxRateSource() {
        return purchaseFxRateSource;
    }

    public LocalDate getPurchaseFxRateAsOf() {
        return purchaseFxRateAsOf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
