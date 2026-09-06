package com.divurve.domain.stress.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 스트레스 테스트 실행 결과 (ERD {@code stress_test_runs}, 요구사항 FR-ST-05).
 *
 * <p><b>삭제하지 않는다.</b> 사용자에게 이미 노출된 계산 근거이기 때문이다(ERD §12).
 * 충격률은 실행 시점 스냅샷이라 시나리오 마스터가 바뀌어도 과거 결과가 그대로 재현된다.
 *
 * <p>효과 3항은 요구사항 §4.8 "주가·환율·총 평가금액 효과 분리"를 그대로 담는다.
 * 적용 순서(주가 → 환율)가 고정이므로 {@code equityEffectKrw + fxEffectKrw = totalEffectKrw} 가
 * 항상 정확히 성립한다.
 *
 * <p>{@code snapshotDate} 는 ERD 상 {@code portfolio_snapshots} 를 가리키지만 그 테이블이 아직 없어
 * 지금은 FK 없이 비워 둔다(V11 마이그레이션 주석 참고).
 */
@Entity
@Table(name = "stress_test_runs")
public class StressTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "scenario_code", nullable = false)
    private String scenarioCode;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "equity_shock_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal equityShockPct;

    @Column(name = "fx_shock_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal fxShockPct;

    @Column(name = "equity_effect_krw", nullable = false, precision = 18, scale = 0)
    private BigDecimal equityEffectKrw;

    @Column(name = "fx_effect_krw", nullable = false, precision = 18, scale = 0)
    private BigDecimal fxEffectKrw;

    @Column(name = "total_effect_krw", nullable = false, precision = 18, scale = 0)
    private BigDecimal totalEffectKrw;

    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용 기본 생성자. */
    protected StressTestRun() {
    }

    private StressTestRun(
            User owner,
            String scenarioCode,
            LocalDate baseDate,
            double equityShockPct,
            double fxShockPct,
            long equityEffectKrw,
            long fxEffectKrw,
            long totalEffectKrw) {
        this.owner = owner;
        this.scenarioCode = scenarioCode;
        this.baseDate = baseDate;
        this.equityShockPct = BigDecimal.valueOf(equityShockPct);
        this.fxShockPct = BigDecimal.valueOf(fxShockPct);
        this.equityEffectKrw = BigDecimal.valueOf(equityEffectKrw);
        this.fxEffectKrw = BigDecimal.valueOf(fxEffectKrw);
        this.totalEffectKrw = BigDecimal.valueOf(totalEffectKrw);
    }

    /**
     * 실행 결과를 남긴다. id·created_at 은 저장 시점에 DB 가 채운다.
     *
     * @param owner            실행한 사용자
     * @param scenarioCode     적용한 시나리오 코드
     * @param baseDate         계산 기준일
     * @param equityShockPct   실행 시점 주가 충격률 스냅샷
     * @param fxShockPct       실행 시점 환율 충격률 스냅샷
     * @param equityEffectKrw  주가 효과
     * @param fxEffectKrw      환율 효과
     * @param totalEffectKrw   총 평가금액 효과
     * @return 저장 전 엔티티
     */
    public static StressTestRun create(
            User owner,
            String scenarioCode,
            LocalDate baseDate,
            double equityShockPct,
            double fxShockPct,
            long equityEffectKrw,
            long fxEffectKrw,
            long totalEffectKrw) {
        return new StressTestRun(
                owner, scenarioCode, baseDate,
                equityShockPct, fxShockPct,
                equityEffectKrw, fxEffectKrw, totalEffectKrw);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public BigDecimal getEquityShockPct() {
        return equityShockPct;
    }

    public BigDecimal getFxShockPct() {
        return fxShockPct;
    }

    public BigDecimal getEquityEffectKrw() {
        return equityEffectKrw;
    }

    public BigDecimal getFxEffectKrw() {
        return fxEffectKrw;
    }

    public BigDecimal getTotalEffectKrw() {
        return totalEffectKrw;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
