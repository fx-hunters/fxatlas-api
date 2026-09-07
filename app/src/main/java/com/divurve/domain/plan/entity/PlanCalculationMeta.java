package com.divurve.domain.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * 계획을 계산할 때 쓴 전제 (플래너 명세 §11.1 "계산 메타데이터").
 *
 * <p>명세 §7 은 이 값을 "결과 재현과 감사 기록" 용도로 규정한다 — 같은 계획을 나중에 다시
 * 계산해도 왜 그 수치가 나왔는지 설명할 수 있어야 한다. 그래서 계산 시점의 환율·스프레드·수수료
 * 가정을 결과와 함께 저장한다.
 *
 * <p>🔒 환율 세 값은 <b>외화 1단위당 원화로 정규화된</b> 값이다 (명세 §7·§21-6).
 * 100엔 기준 고시를 그대로 넣으면 안 된다 — {@code quoteUnit} 은 원본 고시 단위를 기록해 둘 뿐
 * 계산에 다시 적용되지 않는다.
 */
@Embeddable
public class PlanCalculationMeta {

    @Column(name = "policy_version")
    private String policyVersion;

    @Column(name = "rate_as_of")
    private Instant rateAsOf;

    @Column(name = "forecast_as_of")
    private Instant forecastAsOf;

    @Column(name = "base_rate")
    private Double baseRate;

    @Column(name = "rate_low")
    private Double rateLow;

    @Column(name = "rate_high")
    private Double rateHigh;

    @Column(name = "spread_ratio")
    private Double spreadRatio;

    @Column(name = "fee_krw")
    private Long feeKrw;

    @Column(name = "quote_unit")
    private Integer quoteUnit;

    /** JPA 전용 기본 생성자. */
    protected PlanCalculationMeta() {
    }

    private PlanCalculationMeta(Builder builder) {
        this.policyVersion = builder.policyVersion;
        this.rateAsOf = builder.rateAsOf;
        this.forecastAsOf = builder.forecastAsOf;
        this.baseRate = builder.baseRate;
        this.rateLow = builder.rateLow;
        this.rateHigh = builder.rateHigh;
        this.spreadRatio = builder.spreadRatio;
        this.feeKrw = builder.feeKrw;
        this.quoteUnit = builder.quoteUnit;
    }

    /**
     * 계산 메타데이터 빌더.
     *
     * @param policyVersion 계산 정책 버전 ({@code PlannerPolicy.POLICY_VERSION})
     */
    public static Builder builder(String policyVersion) {
        return new Builder(policyVersion);
    }

    /** {@link PlanCalculationMeta} 생성용 빌더. */
    public static final class Builder {
        private final String policyVersion;
        private Instant rateAsOf;
        private Instant forecastAsOf;
        private Double baseRate;
        private Double rateLow;
        private Double rateHigh;
        private Double spreadRatio;
        private Long feeKrw;
        private Integer quoteUnit;

        private Builder(String policyVersion) {
            this.policyVersion = policyVersion;
        }

        public Builder rateAsOf(Instant rateAsOf) {
            this.rateAsOf = rateAsOf;
            return this;
        }

        public Builder forecastAsOf(Instant forecastAsOf) {
            this.forecastAsOf = forecastAsOf;
            return this;
        }

        /** 계산에 쓴 환율 범위. 전부 외화 1단위당 원화로 정규화된 값이어야 한다. */
        public Builder rates(Double low, Double base, Double high) {
            this.rateLow = low;
            this.baseRate = base;
            this.rateHigh = high;
            return this;
        }

        public Builder spreadRatio(Double spreadRatio) {
            this.spreadRatio = spreadRatio;
            return this;
        }

        public Builder feeKrw(Long feeKrw) {
            this.feeKrw = feeKrw;
            return this;
        }

        /** 원본 고시 단위 (JPY 는 100). 기록용이며 계산에 다시 적용되지 않는다. */
        public Builder quoteUnit(Integer quoteUnit) {
            this.quoteUnit = quoteUnit;
            return this;
        }

        public PlanCalculationMeta build() {
            return new PlanCalculationMeta(this);
        }
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getRateAsOf() {
        return rateAsOf;
    }

    public Instant getForecastAsOf() {
        return forecastAsOf;
    }

    public Double getBaseRate() {
        return baseRate;
    }

    public Double getRateLow() {
        return rateLow;
    }

    public Double getRateHigh() {
        return rateHigh;
    }

    public Double getSpreadRatio() {
        return spreadRatio;
    }

    public Long getFeeKrw() {
        return feeKrw;
    }

    public Integer getQuoteUnit() {
        return quoteUnit;
    }
}
