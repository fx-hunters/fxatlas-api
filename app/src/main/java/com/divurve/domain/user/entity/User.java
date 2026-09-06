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

    /** 데모 유저를 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static User createDemo(String email, String name) {
        return new User(email, name, null, null, true);
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

    /** 사용자 이름을 수정한다. */
    public void updateName(String name) {
        this.name = name;
    }
}
