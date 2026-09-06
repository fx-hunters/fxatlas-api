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
 * 원화 자산 (FR-XR-01, ERD v3.0 §6.3). 소유자(User) 기준으로만 조회된다 (NFR-SE-03).
 *
 * <p><b>외화 비중의 분모</b>다: {@code 총자산 = Σ krw_assets + Σ 외화예금 + Σ 보유종목}.
 * 원화 금액이므로 소수점이 없다(ERD 네이밍 규칙 {@code _krw} → 정수).
 */
@Entity
@Table(name = "krw_assets")
public class KrwAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 32)
    private String kind;

    private String label;

    @Column(name = "amount_krw", nullable = false)
    private long amountKrw;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 전용 기본 생성자. */
    protected KrwAsset() {
    }

    private KrwAsset(User owner, String kind, String label, long amountKrw, Instant updatedAt) {
        this.owner = owner;
        this.kind = kind;
        this.label = label;
        this.amountKrw = amountKrw;
        this.updatedAt = updatedAt;
    }

    /** 새 원화 자산을 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static KrwAsset create(User owner, String kind, String label, long amountKrw, Instant now) {
        return new KrwAsset(owner, kind, label, amountKrw, now);
    }

    /** 종류·이름표·금액을 수정한다. */
    public void update(String kind, String label, long amountKrw, Instant now) {
        this.kind = kind;
        this.label = label;
        this.amountKrw = amountKrw;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public long getAmountKrw() {
        return amountKrw;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
