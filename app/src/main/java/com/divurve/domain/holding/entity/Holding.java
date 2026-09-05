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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 보유 종목. 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 * current_price/value_krw 등 계산값은 저장하지 않고 이후 engine 이 산출한다.
 */
@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "avg_price", nullable = false)
    private double avgPrice;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected Holding() {
    }

    private Holding(User owner, String ticker, String currencyCode, double quantity, double avgPrice) {
        this.owner = owner;
        this.ticker = ticker;
        this.currencyCode = currencyCode;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    /** 새 보유 종목을 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static Holding create(User owner, String ticker, String currencyCode, double quantity, double avgPrice) {
        return new Holding(owner, ticker, currencyCode, quantity, avgPrice);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getTicker() {
        return ticker;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
