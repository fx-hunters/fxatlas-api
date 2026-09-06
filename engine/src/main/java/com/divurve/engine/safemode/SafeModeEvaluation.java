package com.divurve.engine.safemode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 안전모드 평가에 필요한 모든 데이터 (명세 3.9).
 * domain 에서 조회한 데이터를 engine 의 순수 함수에 전달하기 위한 값 객체다.
 *
 * <p>모든 필드는 nullable이며, null인 경우 해당 조건은 자동으로 통과 처리된다
 * (외부 데이터 부재 시 판정 불가 → 안전모드 미발동).
 *
 * @param lastUpdateDate 마지막 데이터 업데이트일
 * @param primaryRate 주요 출처 환율
 * @param secondaryRate 보조 출처 환율
 * @param currentRate 현재 환율
 * @param movingAverageRate 20일 이동평균 환율
 * @param currentVolatility 최근 30일 변동성
 * @param historicalAverageVolatility 역사 평균 변동성
 * @param daysUntilDeadline 목표 마감까지 남은 일수
 * @param uncoveredRatio 목표 미확보율 (0.0~1.0)
 * @param consecutiveSkipCount 연속 환전 기회 불이행 횟수
 */
public record SafeModeEvaluation(
    LocalDate lastUpdateDate,
    BigDecimal primaryRate,
    BigDecimal secondaryRate,
    BigDecimal currentRate,
    BigDecimal movingAverageRate,
    BigDecimal currentVolatility,
    BigDecimal historicalAverageVolatility,
    Long daysUntilDeadline,
    BigDecimal uncoveredRatio,
    Long consecutiveSkipCount) {

    /**
     * 빌더를 통한 생성을 권장한다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * SafeModeEvaluation 생성용 빌더.
     */
    public static final class Builder {
        private LocalDate lastUpdateDate;
        private BigDecimal primaryRate;
        private BigDecimal secondaryRate;
        private BigDecimal currentRate;
        private BigDecimal movingAverageRate;
        private BigDecimal currentVolatility;
        private BigDecimal historicalAverageVolatility;
        private Long daysUntilDeadline;
        private BigDecimal uncoveredRatio;
        private Long consecutiveSkipCount;

        public Builder lastUpdateDate(LocalDate lastUpdateDate) {
            this.lastUpdateDate = lastUpdateDate;
            return this;
        }

        public Builder primaryRate(BigDecimal primaryRate) {
            this.primaryRate = primaryRate;
            return this;
        }

        public Builder secondaryRate(BigDecimal secondaryRate) {
            this.secondaryRate = secondaryRate;
            return this;
        }

        public Builder currentRate(BigDecimal currentRate) {
            this.currentRate = currentRate;
            return this;
        }

        public Builder movingAverageRate(BigDecimal movingAverageRate) {
            this.movingAverageRate = movingAverageRate;
            return this;
        }

        public Builder currentVolatility(BigDecimal currentVolatility) {
            this.currentVolatility = currentVolatility;
            return this;
        }

        public Builder historicalAverageVolatility(BigDecimal historicalAverageVolatility) {
            this.historicalAverageVolatility = historicalAverageVolatility;
            return this;
        }

        public Builder daysUntilDeadline(Long daysUntilDeadline) {
            this.daysUntilDeadline = daysUntilDeadline;
            return this;
        }

        public Builder uncoveredRatio(BigDecimal uncoveredRatio) {
            this.uncoveredRatio = uncoveredRatio;
            return this;
        }

        public Builder consecutiveSkipCount(Long consecutiveSkipCount) {
            this.consecutiveSkipCount = consecutiveSkipCount;
            return this;
        }

        public SafeModeEvaluation build() {
            return new SafeModeEvaluation(
                lastUpdateDate,
                primaryRate,
                secondaryRate,
                currentRate,
                movingAverageRate,
                currentVolatility,
                historicalAverageVolatility,
                daysUntilDeadline,
                uncoveredRatio,
                consecutiveSkipCount);
        }
    }
}
