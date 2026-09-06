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
 * 보유 종목. 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 * current_price/value_krw 등 계산값은 저장하지 않고 이후 engine 이 산출한다.
 * 매입 환율(purchaseFxRateKrw)은 FR-ON-04 에 따라 매입 시점의 원화 환산 근거로 저장하며,
 * NFR-DT-01 에 따라 출처(source)·기준일(asOf)을 함께 보존한다.
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

    /** 수량·평균단가를 수정한다. */
    public void updateQuantities(double quantity, double avgPrice) {
        this.quantity = quantity;
        this.avgPrice = avgPrice;
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
