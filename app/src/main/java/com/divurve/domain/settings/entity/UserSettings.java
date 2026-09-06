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
 * 사용자 표시·거래·알림 설정 (이슈 #10, #21). 사용자당 하나.
 * {@code explain_level}(설명 선호 3단계)·{@code explain_domain}(익숙한 설명 분야)은 문구·비유·설명 밀도에만 쓰고
 * 금액·위험 판정 계산에는 절대 들어가지 않는다(FR-MY-03).
 * {@code default_bank_code}·{@code fx_discount_ratio}(주거래 은행·환전 우대율)는 실효 스프레드 계산의 입력이다(FR-MY-04).
 * 알림 4종({@code exchange_schedule_reminder}·{@code review_required_alert}·{@code deadline_approach_alert}·{@code bucket_entry_alert})은
 * 모두 boolean 타입으로 기본값은 true(활성화)다(FR-MY-05, FR-MY-06).
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

    @Column(name = "explain_level", nullable = false)
    private String explainLevel;

    @Column(name = "explain_domain", nullable = false)
    private String explainDomain;

    @Column(name = "exchange_schedule_reminder", nullable = false)
    private boolean exchangeScheduleReminder = true;

    @Column(name = "review_required_alert", nullable = false)
    private boolean reviewRequiredAlert = true;

    @Column(name = "deadline_approach_alert", nullable = false)
    private boolean deadlineApproachAlert = true;

    @Column(name = "bucket_entry_alert", nullable = false)
    private boolean bucketEntryAlert = true;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected UserSettings() {
    }

    private UserSettings(
            User owner, String defaultBankCode, double fxDiscountRatio, String explainLevel, String explainDomain) {
        this.owner = owner;
        this.defaultBankCode = defaultBankCode;
        this.fxDiscountRatio = fxDiscountRatio;
        this.explainLevel = explainLevel;
        this.explainDomain = explainDomain;
        this.exchangeScheduleReminder = true;
        this.reviewRequiredAlert = true;
        this.deadlineApproachAlert = true;
        this.bucketEntryAlert = true;
    }

    /** 새 설정을 만든다. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static UserSettings create(
            User owner, String defaultBankCode, double fxDiscountRatio, String explainLevel, String explainDomain) {
        return new UserSettings(owner, defaultBankCode, fxDiscountRatio, explainLevel, explainDomain);
    }

    /** 표시·거래 설정값을 갱신한다. */
    public void update(String defaultBankCode, double fxDiscountRatio, String explainLevel, String explainDomain) {
        this.defaultBankCode = defaultBankCode;
        this.fxDiscountRatio = fxDiscountRatio;
        this.explainLevel = explainLevel;
        this.explainDomain = explainDomain;
    }

    /** 알림 설정을 갱신한다. */
    public void updateNotifications(
            boolean exchangeScheduleReminder, boolean reviewRequiredAlert,
            boolean deadlineApproachAlert, boolean bucketEntryAlert) {
        this.exchangeScheduleReminder = exchangeScheduleReminder;
        this.reviewRequiredAlert = reviewRequiredAlert;
        this.deadlineApproachAlert = deadlineApproachAlert;
        this.bucketEntryAlert = bucketEntryAlert;
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

    public String getExplainLevel() {
        return explainLevel;
    }

    public String getExplainDomain() {
        return explainDomain;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isExchangeScheduleReminder() {
        return exchangeScheduleReminder;
    }

    public boolean isReviewRequiredAlert() {
        return reviewRequiredAlert;
    }

    public boolean isDeadlineApproachAlert() {
        return deadlineApproachAlert;
    }

    public boolean isBucketEntryAlert() {
        return bucketEntryAlert;
    }
}
