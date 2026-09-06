package com.divurve.domain.settings.entity;

import com.divurve.domain.user.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 사용자 투자성향 프로필 (이슈 #10, FR-MY-02). 사용자당 하나이며, 재진단하면 등급·점수·응답 이력을 덮어쓴다.
 * 등급값({@code risk_type})은 이후 집중도 기준선·버킷 비율의 입력이 된다.
 *
 * <p>등급·점수는 engine {@code RiskProfileScorer} 가 산출한 값을 받아 저장만 한다 — 엔티티는 계산하지 않는다.
 * 설명 프로필인 {@link UserSettings#getExplainLevel()} 와는 분리 관리한다(FR-MY-03).
 */
@Entity
@Table(name = "risk_profiles")
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "risk_type", nullable = false)
    private String riskType;

    @Column(name = "score", nullable = false)
    private int score;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "risk_profile_answers",
            joinColumns = @JoinColumn(name = "risk_profile_id"))
    private List<RiskAnswer> answers = new ArrayList<>();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected RiskProfile() {
    }

    private RiskProfile(User owner, String riskType, int score, List<RiskAnswer> answers) {
        this.owner = owner;
        this.riskType = riskType;
        this.score = score;
        this.answers = new ArrayList<>(answers);
    }

    /** 새 성향 프로필을 만든다. id/created_at 은 저장 시점에 DB 가 채운다. */
    public static RiskProfile create(User owner, String riskType, int score, List<RiskAnswer> answers) {
        return new RiskProfile(owner, riskType, score, answers);
    }

    /** 재진단 결과로 등급·점수·응답 이력을 덮어쓴다. */
    public void reassess(String riskType, int score, List<RiskAnswer> answers) {
        this.riskType = riskType;
        this.score = score;
        this.answers.clear();
        this.answers.addAll(answers);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getRiskType() {
        return riskType;
    }

    public int getScore() {
        return score;
    }

    public List<RiskAnswer> getAnswers() {
        return List.copyOf(answers);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
