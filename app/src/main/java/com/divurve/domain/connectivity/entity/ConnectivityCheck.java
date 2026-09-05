package com.divurve.domain.connectivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 프론트·DB 연동 확인용 테스트 엔티티. 테이블 {@code connectivity_check} 에 매핑된다.
 * created_at 은 DB 기본값(now())으로 채워지며, insert 후 재조회 시 값이 실린다.
 */
@Entity
@Table(name = "connectivity_check")
public class ConnectivityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected ConnectivityCheck() {
    }

    private ConnectivityCheck(String message) {
        this.message = message;
    }

    /** 새 행을 만들 때 사용하는 팩토리. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static ConnectivityCheck create(String message) {
        return new ConnectivityCheck(message);
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
