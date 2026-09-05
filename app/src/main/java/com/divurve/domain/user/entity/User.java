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

    @Column(name = "is_demo", nullable = false)
    private boolean isDemo;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected User() {
    }

    private User(String email, String name, boolean isDemo) {
        this.email = email;
        this.name = name;
        this.isDemo = isDemo;
    }

    /** 새 사용자를 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static User create(String email, String name, boolean isDemo) {
        return new User(email, name, isDemo);
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

    public boolean isDemo() {
        return isDemo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
