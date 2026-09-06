package com.divurve.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 서비스 사용자. 모든 자산·목표 데이터의 소유자 루트다 (NFR-SE-03).
 * 테이블 {@code users} 에 매핑된다. id/created_at 은 저장 시점에 DB 가 채운다.
 *
 * 회원가입 유저는 passwordHash를 가지며, 데모 유저는 null이다(로그인 불가).
 * onboardingPurpose는 온보딩 시 선택한 투자 목적(OVERSEAS_INVESTMENT 또는 FOREIGN_CURRENCY_GOAL).
 *
 * <p>{@code onboarded_at} 이 NULL 이면 초기 설정으로 보낸다(ERD v3.0 §4.B, FR-IS-01·FR-IS-07).
 * {@code POST /me/onboarding/complete} 가 이 값을 기록하며, 전부 건너뛰어도 호출 가능하다(FR-IS-05).
 * 데모 세션은 샘플 데이터가 이미 채워져 있으므로 생성 시점에 완료로 표시한다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "onboarding_purpose")
    private String onboardingPurpose;

    @Column(name = "is_demo", nullable = false)
    private boolean isDemo;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected User() {
    }

    private User(String email, String name, String passwordHash, String onboardingPurpose, boolean isDemo) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.onboardingPurpose = onboardingPurpose;
        this.isDemo = isDemo;
    }

    /**
     * 데모 유저를 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다.
     * 둘러보기 계정은 샘플 자산이 이미 채워진 상태로 시작하므로 초기 설정을 완료한 것으로 표시한다(FR-IS-09).
     */
    public static User createDemo(String email, String name) {
        User demo = new User(email, name, null, null, true);
        demo.onboardedAt = Instant.now();
        return demo;
    }

    /** 일반 회원가입 유저를 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static User create(String email, String name, String passwordHash, String onboardingPurpose) {
        return new User(email, name, passwordHash, onboardingPurpose, false);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getOnboardingPurpose() {
        return onboardingPurpose;
    }

    public boolean isDemo() {
        return isDemo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOnboardedAt() {
        return onboardedAt;
    }

    /** 초기 설정을 마쳤는지 여부. 로그인 응답의 {@code onboarded} 가 이 값이다(FR-IS-01). */
    public boolean isOnboarded() {
        return onboardedAt != null;
    }

    /**
     * 초기 설정 완료 시각을 기록한다. 이미 완료한 사용자는 시각을 덮어쓰지 않는다 —
     * 재방문 시 초기 설정을 반복하지 않는다는 요구(FR-IS-07)에서 최초 완료 시각이 의미를 갖는다.
     */
    public void completeOnboarding(Instant completedAt) {
        if (this.onboardedAt == null) {
            this.onboardedAt = completedAt;
        }
    }

    /** 사용자 이름을 수정한다. */
    public void updateName(String name) {
        this.name = name;
    }
}
