package com.divurve.api.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 계획 응답 (플래너 명세 §11). 미리보기와 확정·조회가 <b>같은 구조</b>를 쓴다 —
 * 사용자가 미리보기에서 본 화면과 저장된 계획의 화면이 달라지면 안 되기 때문이다.
 *
 * <p>필드명은 DB 컬럼명 그대로 쓰고 전역 SNAKE_CASE 전략이 변환한다. 숫자 경계가 있는
 * {@code interval_80} 만 {@code @JsonProperty} 로 키를 고정한다 — 전략은 대문자 앞에만
 * {@code _} 를 넣어 {@code interval80} 이 되기 때문이다.
 *
 * <p>계획 상태와 회차 상태는 명세 §13 의 어휘를 소문자로 쓴다.
 *
 * @param planId          저장된 계획 ID. 미리보기는 {@code null}
 * @param goalId          목표 ID. 목표 저장 전 미리보기는 {@code null}
 * @param version         계획 버전. 미리보기는 {@code null}
 * @param calculationMeta 계산 기준·가정·출처 (명세 §11.1)
 * @param goal            목표 요약 (명세 §11.2)
 * @param summary         계획 요약 (명세 §11.3)
 * @param steps           회차 목록 (명세 §11.4)
 * @param warnings        경고 코드 (명세 §20·§21-8)
 * @param disclaimer      이 계획이 보장하는 것과 보장하지 않는 것 (명세 §2·§26)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "계획 응답 (플래너 명세 §11)")
public record PlanResponse(
        String planId,
        String goalId,
        Integer version,
        CalculationMeta calculationMeta,
        GoalSummary goal,
        Summary summary,
        List<Step> steps,
        List<String> warnings,
        String disclaimer) {

    /**
     * 이 계획이 무엇이 아닌지 (명세 §2·§26).
     *
     * <p>명세는 Planner 의 출력이 <b>조건부 계획</b>이며 목표 달성이나 환율 범위를 보장하지
     * 않는다고 반복해서 못박는다. 응답에 이 문장을 함께 실어 화면이 어디에 놓든 빠지지 않게 한다.
     */
    public static final String DISCLAIMER =
            "이 계획은 조건부 계산 결과이며 목표 달성이나 환율 범위를 보장하지 않습니다. "
                    + "Divurve 는 환전을 실행하지 않으며 환율 방향이나 매매 시점을 추천하지 않습니다.";

    /**
     * 계산 메타데이터 (명세 §11.1). 결과 재현과 감사 기록을 위한 값이다 (명세 §7).
     *
     * @param calculatedAt   계산 기준 시각
     * @param rateAsOf       환율 기준 시각
     * @param forecastAsOf   Forecast 기준 시각. 구간을 얻지 못했으면 {@code null}
     * @param policyVersion  계산 정책 버전
     * @param currencyCode   목표 통화
     * @param quoteUnit      원본 고시 단위 (JPY 100). 환율은 이미 1단위로 정규화돼 있다
     * @param rates          계산에 쓴 환율 범위 (외화 1단위당 원화)
     * @param spreadRatio    스프레드 가정
     * @param feeKrw         회차당 정액 수수료 가정 (원)
     */
    public record CalculationMeta(
            Instant calculatedAt,
            Instant rateAsOf,
            Instant forecastAsOf,
            String policyVersion,
            String currencyCode,
            int quoteUnit,
            Rates rates,
            double spreadRatio,
            long feeKrw) {

        /**
         * 계산에 쓴 환율 범위.
         *
         * <p>방향 전망이 아니다 — 같은 외화 금액을 준비할 때 환율에 따라 원화 비용이 얼마나
         * 달라질 수 있는지를 나타낸다 (명세 §9.3).
         *
         * @param low  하단
         * @param base 기준
         * @param high 상단
         */
        public record Rates(double low, double base, double high) {
        }
    }

    /**
     * 목표 요약 (명세 §11.2).
     *
     * @param goalType               {@code deadline} / {@code recurring}
     * @param purpose                목적
     * @param currencyCode           목표 통화
     * @param targetAmount           마감형 목표 외화 총액. 정기형은 {@code null}
     * @param roundBudgetKrw         정기형 회차 예산. 마감형은 {@code null}
     * @param allocatedHoldingAmount 목표에 배정한 외화
     * @param remainingAmount        앞으로 준비해야 할 외화
     * @param targetDate             마감형 목표일 / 정기형 점검 종료일
     */
    public record GoalSummary(
            String goalType,
            String purpose,
            String currencyCode,
            Double targetAmount,
            Long roundBudgetKrw,
            double allocatedHoldingAmount,
            double remainingAmount,
            LocalDate targetDate) {
    }

    /**
     * 계획 요약 (명세 §11.3).
     *
     * @param status                계획 상태 (명세 §13.1)
     * @param planEndDate           계획 종료일 — 마감 버퍼를 뺀 날 (명세 §9.4)
     * @param totalRounds           전체 회차 수
     * @param completedRounds       완료 회차 수
     * @param scheduledRounds       예정 회차 수
     * @param skippedRounds         건너뛴 회차 수
     * @param nextActionSeq         지금 확인·기록할 회차 번호. 없으면 {@code null}
     * @param estimatedCost         예상 원화 비용 범위 (명세 §9.3)
     * @param budgetState           예산 가능 상태 (명세 §9.6). 정기형은 {@code null}
     * @param cumulativeAcquisition 정기형 점검 시점의 누적 확보 외화 범위 (명세 §10.3)
     */
    public record Summary(
            String status,
            LocalDate planEndDate,
            int totalRounds,
            int completedRounds,
            int scheduledRounds,
            int skippedRounds,
            Integer nextActionSeq,
            CostRange estimatedCost,
            String budgetState,
            AcquisitionRange cumulativeAcquisition) {
    }

    /**
     * 회차 (명세 §11.4).
     *
     * @param seq            회차 번호
     * @param scheduledDate  예정일
     * @param amount         계획 외화 금액
     * @param budgetKrw      정기형 회차 예산. 마감형은 {@code null}
     * @param estimatedCost  회차별 예상 비용 범위
     * @param acquisition    정기형 확보 가능 외화 범위. 마감형은 {@code null}
     * @param executedAmount 실행한 외화 금액
     * @param executedRate   실행 환율
     * @param executedDate   실행일
     * @param status         회차 상태 (명세 §13.2)
     * @param nextAction     지금 확인·기록할 다음 행동인지
     */
    public record Step(
            int seq,
            LocalDate scheduledDate,
            double amount,
            Long budgetKrw,
            CostRange estimatedCost,
            AcquisitionRange acquisition,
            double executedAmount,
            Double executedRate,
            LocalDate executedDate,
            String status,
            boolean nextAction) {
    }

    /**
     * 환율 범위별 예상 원화 비용 (명세 §9.3). 비용은 환율에 비례한다.
     *
     * @param lowKrw  환율 하단 기준
     * @param baseKrw 기준 환율
     * @param highKrw 환율 상단 기준
     */
    public record CostRange(long lowKrw, long baseKrw, long highKrw) {
    }

    /**
     * 같은 예산으로 확보할 수 있는 외화 범위 (명세 §10.2).
     *
     * <p>비용과 <b>방향이 반대</b>다 — 환율이 높을수록 확보 외화는 줄어들므로 {@code low} 가
     * 환율 상단에서 나온다.
     *
     * @param low  가장 적게 확보하는 경우
     * @param base 기준 환율
     * @param high 가장 많이 확보하는 경우
     */
    public record AcquisitionRange(double low, double base, double high) {
    }
}
