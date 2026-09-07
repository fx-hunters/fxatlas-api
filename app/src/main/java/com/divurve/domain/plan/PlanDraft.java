package com.divurve.domain.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 계산된 계획 (플래너 명세 §11). <b>저장되지 않은 값</b>이며, 확정 시에만 영속화된다.
 *
 * <p>명세 §11 의 네 블록을 그대로 담는다 — 계산 메타데이터(§11.1) · 목표 요약(§11.2) ·
 * 계획 요약(§11.3) · 회차 목록(§11.4). API DTO 가 아니라 도메인 값이므로 직렬화 관심사는 없다.
 *
 * <p>Planner 가 내놓는 것은 <b>조건부 계획</b>이다 — 목표 달성이나 환율 범위를 보장하지 않는다
 * (명세 §2).
 *
 * <p>비용·확보 범위를 engine 타입이 아니라 자체 record 로 담는다 — api 레이어가 응답을 만들 때
 * engine 에 닿지 않아야 하기 때문이다 (아키텍처 규칙: engine 은 domain 에서만 접근한다).
 *
 * @param calculatedAt 계산 기준 시각 (명세 §11.1)
 * @param policyVersion 계산 정책 버전 (명세 §7·§11.1)
 * @param rateContext  계산에 쓴 환율·비용 전제
 * @param goal         목표 요약 (명세 §11.2)
 * @param summary      계획 요약 (명세 §11.3)
 * @param steps        회차 목록 (명세 §11.4)
 * @param warnings     경고 코드 — 데이터 제약·예산 초과 등을 숨기지 않는다 (명세 §20·§21-8)
 */
public record PlanDraft(
        Instant calculatedAt,
        String policyVersion,
        PlanRateContext rateContext,
        GoalSummary goal,
        Summary summary,
        List<Step> steps,
        List<String> warnings) {

    public PlanDraft {
        Objects.requireNonNull(calculatedAt, "calculatedAt");
        Objects.requireNonNull(rateContext, "rateContext");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /**
     * 목표 요약 (명세 §11.2).
     *
     * @param goalType             마감형/정기형
     * @param purpose              목적
     * @param currencyCode         목표 통화
     * @param targetAmount         마감형 목표 외화 총액 {@code T}. 정기형은 {@code null}
     * @param roundBudgetKrw       정기형 회차 예산. 마감형은 {@code null}
     * @param allocatedHoldingAmount 목표에 배정한 외화 {@code H}
     * @param remainingAmount      앞으로 준비해야 할 외화 {@code R = max(T - H, 0)}
     * @param targetDate           마감형 목표일 / 정기형 점검 종료일
     * @param priorityConstraint   사용자가 선택한 우선 조건 (명세 §17)
     */
    public record GoalSummary(
            String goalType,
            String purpose,
            String currencyCode,
            BigDecimal targetAmount,
            Long roundBudgetKrw,
            BigDecimal allocatedHoldingAmount,
            BigDecimal remainingAmount,
            LocalDate targetDate,
            String priorityConstraint) {
    }

    /**
     * 계획 요약 (명세 §11.3).
     *
     * @param status            계획 상태 (명세 §13.1)
     * @param planEndDate       계획 종료일 {@code targetDate - businessDayBuffer} (명세 §9.4)
     * @param totalRounds       전체 회차 수 {@code K}
     * @param completedRounds   완료 회차 수
     * @param scheduledRounds   예정 회차 수
     * @param skippedRounds     건너뛴 회차 수
     * @param nextActionSeq     지금 확인·기록할 다음 행동의 회차 번호. 없으면 {@code null}
     * @param costRange         예상 원화 비용 범위 (명세 §9.3). 정기형은 회차 예산의 합
     * @param budgetState       예산 가능 상태 (명세 §9.6). 정기형은 {@code null} — 예산이 곧 입력이다
     * @param cumulativeAcquisition 정기형의 점검 시점 누적 외화 범위 (명세 §10.3). 마감형은 {@code null}
     */
    public record Summary(
            String status,
            LocalDate planEndDate,
            int totalRounds,
            int completedRounds,
            int scheduledRounds,
            int skippedRounds,
            Integer nextActionSeq,
            CostRange costRange,
            String budgetState,
            AcquisitionRange cumulativeAcquisition) {
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
     * 비용과 <b>방향이 반대</b>다 — 환율이 높을수록 확보 외화는 줄어든다.
     *
     * @param low  가장 적게 확보하는 경우 (환율 상단)
     * @param base 기준 환율
     * @param high 가장 많이 확보하는 경우 (환율 하단)
     */
    public record AcquisitionRange(BigDecimal low, BigDecimal base, BigDecimal high) {
    }

    /**
     * 회차 (명세 §11.4).
     *
     * <p>마감형은 {@code amount}(준비할 외화)가, 정기형은 {@code budgetKrw}(쓸 원화)가 고정값이다.
     * 정기형의 {@code amount} 는 기준 환율에서 확보할 수 있는 <b>추정</b> 외화이며,
     * {@code acquisition} 이 그 범위를 보여준다.
     *
     * @param seq            회차 번호 (1부터)
     * @param scheduledDate  예정일
     * @param amount         계획 외화 금액
     * @param budgetKrw      정기형 회차 예산. 마감형은 {@code null}
     * @param costRange      회차별 예상 비용 범위
     * @param acquisition    정기형 확보 가능 외화 범위. 마감형은 {@code null}
     * @param executedAmount 실행한 외화 금액 (미리보기는 0)
     * @param executedRate   실행 환율
     * @param executedDate   실행일
     * @param status         회차 상태 (명세 §13.2)
     * @param nextAction     지금 확인·기록할 다음 행동인지
     */
    public record Step(
            int seq,
            LocalDate scheduledDate,
            BigDecimal amount,
            Long budgetKrw,
            CostRange costRange,
            AcquisitionRange acquisition,
            BigDecimal executedAmount,
            Double executedRate,
            LocalDate executedDate,
            String status,
            boolean nextAction) {
    }
}
