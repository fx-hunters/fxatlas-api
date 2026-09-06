package com.divurve.engine.planner;

import java.util.Objects;

/**
 * 플래너 계산 정책 상수 (플래너 명세 §7·§9.4).
 *
 * <p>계산에 쓰인 정책은 결과와 함께 저장·반환되어야 한다 — 명세 §11.1 이 응답에
 * {@code 계산 정책 버전} 을 요구하고, §7 이 이를 "결과 재현과 감사 기록" 용도로 규정한다.
 * 이 클래스의 상수가 바뀌면 {@link #POLICY_VERSION} 도 함께 올리고, 커밋 타입은
 * {@code calc} 로 변경 전/후 수치를 남긴다.
 */
public final class PlannerPolicy {

    /**
     * 계산 정책 버전 (명세 §7·§11.1). 균등 회차 + 환율 범위 비용 모델.
     *
     * <p>이전 모델(안전/기회 버킷 + 몬테카를로 달성확률)은 명세 §23 이 산출 근거 불명으로
     * 지목해 채택하지 않았다 — §24 는 MVP 계산 정책을 균등 회차로 확정한다.
     */
    public static final String POLICY_VERSION = "plan-2026.09.1-equal-split";

    /** 마감형 기본 영업일 버퍼 (명세 §9.4 "기타 마감형"). */
    public static final int DEFAULT_BUSINESS_DAY_BUFFER = 3;

    /** 학비·납부 목적의 영업일 버퍼 (명세 §9.4) — 송금 처리에 더 긴 여유가 필요하다. */
    public static final int TUITION_BUSINESS_DAY_BUFFER = 5;

    private PlannerPolicy() {
    }

    /**
     * 목적별 마감 버퍼 영업일 수 (명세 §9.4).
     *
     * <p>{@code planEndDate = targetDate - businessDayBuffer} 로 쓰인다. 목적 코드는 명세 §5.1 의
     * {@code travel/tuition/investment/deposit/custom} 이며 대소문자를 가리지 않는다 — 기존
     * {@code goals.purpose} 에 대문자 코드가 저장돼 있기 때문이다.
     *
     * @param purpose 목적 코드
     * @return 버퍼 영업일 수 (학비·납부 5, 그 외 3)
     */
    public static int businessDayBufferFor(String purpose) {
        Objects.requireNonNull(purpose, "purpose");
        return "tuition".equalsIgnoreCase(purpose)
                ? TUITION_BUSINESS_DAY_BUFFER
                : DEFAULT_BUSINESS_DAY_BUFFER;
    }
}
