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
 * 알림 스위치 5종({@code notify_step_due}·{@code notify_regime_shift}·{@code notify_deadline_near}·
 * {@code notify_target_zone}·{@code notify_concentration})은 ERD v3.0 {@code user_settings} 그대로이며,
 * {@code notify_target_zone} 만 기본값 false 다(FR-MY-05, FR-MY-06). 명세 §3 마이페이지 표에 따라
 * {@code GET/PUT /me/settings} 가 설명 선호와 함께 다룬다.
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

    /** 회차 집행 예정 알림. */
    @Column(name = "notify_step_due", nullable = false)
    private boolean notifyStepDue = true;

    /** 시장 국면 전환 알림. */
    @Column(name = "notify_regime_shift", nullable = false)
    private boolean notifyRegimeShift = true;

    /** 마감 임박 알림. */
    @Column(name = "notify_deadline_near", nullable = false)
    private boolean notifyDeadlineNear = true;

    /** 목표 구간 진입 알림. ERD 기본값만 false 다. */
    @Column(name = "notify_target_zone", nullable = false)
    private boolean notifyTargetZone = false;

    /** 집중도 경고 알림. */
    @Column(name = "notify_concentration", nullable = false)
    private boolean notifyConcentration = true;

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
        this.notifyStepDue = true;
        this.notifyRegimeShift = true;
        this.notifyDeadlineNear = true;
        this.notifyTargetZone = false;
        this.notifyConcentration = true;
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

    /** 알림 스위치 5종을 갱신한다 (ERD user_settings). */
    public void updateNotifications(
            boolean notifyStepDue,
            boolean notifyRegimeShift,
            boolean notifyDeadlineNear,
            boolean notifyTargetZone,
            boolean notifyConcentration) {
        this.notifyStepDue = notifyStepDue;
        this.notifyRegimeShift = notifyRegimeShift;
        this.notifyDeadlineNear = notifyDeadlineNear;
        this.notifyTargetZone = notifyTargetZone;
        this.notifyConcentration = notifyConcentration;
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

    public boolean isNotifyStepDue() {
        return notifyStepDue;
    }

    public boolean isNotifyRegimeShift() {
        return notifyRegimeShift;
    }

    public boolean isNotifyDeadlineNear() {
        return notifyDeadlineNear;
    }

    public boolean isNotifyTargetZone() {
        return notifyTargetZone;
    }

    public boolean isNotifyConcentration() {
        return notifyConcentration;
    }
}
