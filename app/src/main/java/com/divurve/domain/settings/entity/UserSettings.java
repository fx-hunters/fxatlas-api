package com.divurve.domain.settings.entity;

import com.divurve.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 사용자 표시·거래 설정 (이슈 #10, FR-MY-03·FR-MY-04). 사용자당 하나.
 * {@code display_mode}(설명/표시 프로필)는 금액·위험 판정에 영향을 주지 않으며 성향 프로필과 분리 관리한다(FR-MY-03).
 * {@code default_bank_code}·{@code fx_discount_ratio}(주거래 은행·환전 우대율)는 실효 스프레드 계산의 입력이다(FR-MY-04).
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "default_bank_code")
    private String defaultBankCode;

    @Column(name = "fx_discount_ratio", nullable = false)
    private double fxDiscountRatio;

    @Column(name = "display_mode", nullable = false)
    private String displayMode;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected UserSettings() {
    }

    private UserSettings(User owner, String defaultBankCode, double fxDiscountRatio, String displayMode) {
        this.owner = owner;
        this.defaultBankCode = defaultBankCode;
        this.fxDiscountRatio = fxDiscountRatio;
        this.displayMode = displayMode;
    }

    /** 새 설정을 만든다. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static UserSettings create(User owner, String defaultBankCode, double fxDiscountRatio, String displayMode) {
        return new UserSettings(owner, defaultBankCode, fxDiscountRatio, displayMode);
    }

    /** 설정값을 갱신한다. */
    public void update(String defaultBankCode, double fxDiscountRatio, String displayMode) {
        this.defaultBankCode = defaultBankCode;
        this.fxDiscountRatio = fxDiscountRatio;
        this.displayMode = displayMode;
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getDefaultBankCode() {
        return defaultBankCode;
    }

    public double getFxDiscountRatio() {
        return fxDiscountRatio;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
