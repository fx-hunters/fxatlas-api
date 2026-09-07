package com.divurve.api.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 회차 완료 요청 (플래너 명세 §14).
 *
 * <p>{@code execution_key} 는 <b>중복 반영을 막는 멱등 키</b>다 — 명세 §14·§21-12 는 같은 완료
 * 요청이 두 번 전송돼도 두 번 반영되지 않을 것을 요구한다. 클라이언트가 요청마다 새 키를 만들고
 * 재전송 시에는 <b>같은 키</b>를 보낸다. 키를 생략하면 멱등 보장이 없다.
 *
 * @param executedAmount 실제 확보한 외화 금액
 * @param executedRate   실제 적용된 환율 (외화 1단위당 원화)
 * @param executedDate   실행 날짜. 없으면 오늘
 * @param executionKey   멱등 키
 */
@Schema(description = "회차 완료 요청 (플래너 명세 §14)")
public record StepCompleteRequest(
        @PositiveOrZero(message = "executed_amount 는 0 이상이어야 합니다.") double executedAmount,
        Double executedRate,
        LocalDate executedDate,
        String executionKey) {
}
