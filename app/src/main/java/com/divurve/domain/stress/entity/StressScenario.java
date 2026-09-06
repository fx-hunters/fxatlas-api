package com.divurve.domain.stress.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 스트레스 시나리오 마스터 (ERD {@code stress_scenarios}, 요구사항 FR-ST-01).
 *
 * <p>충격률은 <b>가정값</b>이며 예측이 아니다(FR-ST-04). 사용자에게는 {@link #getAssumptionNote()} 와
 * {@link #getReferenceEvent()} 를 함께 노출해 어떤 가정인지 확인할 수 있게 한다.
 *
 * <p>부호 규약(FR-CM-05): {@code fxShockPct > 0} 은 USD/KRW 상승 = 원화 약세 = 외화자산 평가액 증가.
 *
 * <p>시나리오 마스터가 나중에 바뀌어도 과거 실행 결과는 변하지 않는다 —
 * {@link StressTestRun} 이 실행 시점 충격률을 자기 컬럼에 복사해 두기 때문이다.
 */
@Entity
@Table(name = "stress_scenarios")
public class StressScenario {

    @Id
    @Column(name = "scenario_code", nullable = false)
    private String scenarioCode;

    @Column(name = "name_ko", nullable = false)
    private String nameKo;

    @Column(name = "equity_shock_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal equityShockPct;

    @Column(name = "fx_shock_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal fxShockPct;

    @Column(name = "reference_event")
    private String referenceEvent;

    @Column(name = "assumption_note")
    private String assumptionNote;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    /** JPA 전용 기본 생성자. */
    protected StressScenario() {
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public String getNameKo() {
        return nameKo;
    }

    public BigDecimal getEquityShockPct() {
        return equityShockPct;
    }

    public BigDecimal getFxShockPct() {
        return fxShockPct;
    }

    public String getReferenceEvent() {
        return referenceEvent;
    }

    public String getAssumptionNote() {
        return assumptionNote;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public short getSortOrder() {
        return sortOrder;
    }
}
