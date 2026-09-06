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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 위험성향 프로필 (API 명세 v2 §5.1·§5.2, ERD v3.0 {@code risk_profiles}). 사용자당 하나이며,
 * 간편 재응답은 같은 행의 등급·점수·응답을 덮어쓰고, 상세 진단은 <b>등급·점수를 건드리지 않고</b>
 * {@code detail_progress}/{@code detail_answers} 만 갱신한다(FR-DG-05).
 *
 * <p>Q1~Q3 중 하나라도 미응답이면 {@code status=not_measured} 이고 {@code risk_type}·{@code score} 는 {@code null} 이다 —
 * 임의의 기본 성향을 채워 넣지 않는다(FR-DG-02, FR-IS-06). 응답만 저장된 행이 재개(FR-DG-04)의 근거가 된다.
 *
 * <p>등급·점수·기준선은 engine {@code RiskProfileScorer} 가 산출한 값을 받아 저장만 한다 — 엔티티는 계산하지 않는다.
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

    /** 진단 상태 — {@code not_measured} / {@code simple_done} / {@code detail_done} (ERD diagnosis_status). */
    @Column(name = "status", nullable = false)
    private String status;

    /** 대표 유형 코드. 미측정이면 {@code null}. */
    @Column(name = "risk_type")
    private String riskType;

    /** Q1~Q3 합계 원점수(0~9). 미측정이면 {@code null}. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "concentration_threshold", precision = 5, scale = 4)
    private BigDecimal concentrationThreshold;

    @Column(name = "safe_ratio_adjust", precision = 5, scale = 4)
    private BigDecimal safeRatioAdjust;

    /** 간편 진단 응답 원본 {@code {"q1":"B", ...}}. 부분 응답도 그대로 저장한다(재개 근거). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> answers = new LinkedHashMap<>();

    /** 상세 진단 완료 응답. 완료 전에는 {@code null}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_answers", columnDefinition = "jsonb")
    private Map<String, String> detailAnswers;

    /** 상세 진단 중단 시점 응답(재개용). 완료되면 {@code detail_answers} 로 옮기고 비운다(ERD §11). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_progress", columnDefinition = "jsonb")
    private Map<String, String> detailProgress;

    /** 대표 유형이 확정된 날짜. 마이페이지 `진단일`(FR-MY-04). */
    @Column(name = "diagnosed_on")
    private LocalDate diagnosedOn;

    /** 사용자가 유형을 직접 지정했는지 여부 (ERD). 현재는 항상 false. */
    @Column(name = "is_manual", nullable = false)
    private boolean isManual;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 전용 기본 생성자. */
    protected RiskProfile() {
    }

    private RiskProfile(User owner, String status) {
        this.owner = owner;
        this.status = status;
        this.isManual = false;
    }

    /**
     * 아직 유형이 없는(미측정) 프로필을 만든다. id/created_at 은 저장 시점에 DB 가 채운다.
     *
     * @param notMeasuredStatus 미측정 상태 코드
     */
    public static RiskProfile start(User owner, String notMeasuredStatus) {
        return new RiskProfile(owner, notMeasuredStatus);
    }

    /**
     * 간편 진단 응답과 산출 결과를 반영한다. 산출 결과가 없으면(=미응답 문항 존재) 응답만 저장하고
     * 유형·점수·기준선을 비운다 — 임의의 기본 성향을 만들지 않는다(FR-DG-02).
     */
    public void applySimple(
            Map<String, String> answers,
            String status,
            String riskType,
            Integer score,
            BigDecimal concentrationThreshold,
            BigDecimal safeRatioAdjust,
            LocalDate diagnosedOn) {
        this.answers = new LinkedHashMap<>(answers);
        this.status = status;
        this.riskType = riskType;
        this.score = score;
        this.concentrationThreshold = concentrationThreshold;
        this.safeRatioAdjust = safeRatioAdjust;
        this.diagnosedOn = diagnosedOn;
    }

    /**
     * 상세 진단 응답을 반영한다. <b>유형·점수는 건드리지 않는다</b>(FR-DG-05).
     * 완료되면 {@code detail_answers} 로 옮기고 {@code detail_progress} 를 비운다(ERD §11).
     *
     * @param answered  Q4~Q6 누적 응답
     * @param completed Q4~Q6 을 모두 채웠는지 여부
     * @param status    반영 후 진단 상태
     */
    public void applyDetail(Map<String, String> answered, boolean completed, String status) {
        Map<String, String> copy = new LinkedHashMap<>(answered);
        this.detailAnswers = completed ? copy : null;
        this.detailProgress = completed ? null : copy;
        this.status = status;
    }

    /** 상세 진단 응답(완료본 우선, 없으면 진행 중인 것). 둘 다 없으면 빈 맵. */
    public Map<String, String> detailAnswered() {
        if (detailAnswers != null) {
            return Map.copyOf(detailAnswers);
        }
        if (detailProgress != null) {
            return Map.copyOf(detailProgress);
        }
        return Map.of();
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getStatus() {
        return status;
    }

    public String getRiskType() {
        return riskType;
    }

    public Integer getScore() {
        return score;
    }

    public BigDecimal getConcentrationThreshold() {
        return concentrationThreshold;
    }

    public BigDecimal getSafeRatioAdjust() {
        return safeRatioAdjust;
    }

    public Map<String, String> getAnswers() {
        return Map.copyOf(answers);
    }

    public Map<String, String> getDetailAnswers() {
        return detailAnswers == null ? null : Map.copyOf(detailAnswers);
    }

    public Map<String, String> getDetailProgress() {
        return detailProgress == null ? null : Map.copyOf(detailProgress);
    }

    public LocalDate getDiagnosedOn() {
        return diagnosedOn;
    }

    public boolean isManual() {
        return isManual;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
