package com.divurve.engine.bucket;

import com.divurve.engine.EngineComponent;
import java.util.Objects;

/**
 * 목표 종류·투자성향에 따른 안전/기회 버킷 비율 산출 (FR-RT-06/07).
 *
 * 목적별 상한(안전 하한, 즉 기회 상한):
 * - 해외주식적립: 50% (하한 35%)
 * - 일회성매수: 70% (50%)
 * - 여행: 85% (70%)
 * - 학비송금: 95% (90%)
 *
 * <p><b>⚠ 요구사항 v2 §4.12 미확정</b> — 이 클래스의 상수와 판정 기준은 <b>후보일 뿐 확정
 * 요구사항이 아니다</b>. 요구사항 v2 §4.12 는 Route 의 목적함수 · 최소 입력값 · 안전/기회 버킷
 * 존재와 비율 · 목적별 하한선 · 권장 분할 회차 · 몬테카를로 적용 여부 · 달성 확률 정의를 전부
 * 미확정으로 선언했고, 기존 문서의 50/70/85/95% 비율과 4~8회 권장값도 후보값이다.
 * API 명세 v2 §6.3 은 확정 전까지 이 값들을 <b>명세하지 않는다</b>고 못박는다.
 *
 * <p>그래서 계산기는 남겨 두되 {@code route.enabled} 기능 플래그가 꺼진 동안에는 호출되지 않는다
 * — 호출 경로인 {@code /api/v1/plans/*} 가 501 을 반환한다. 값이 확정되면 이 주석과 상수를 함께
 * 갱신하고, 커밋 타입은 {@code calc} 로 변경 전/후 수치를 남긴다.
 */
@EngineComponent
public class BucketAllocator {

    private static final double DEFAULT_SAFE_RATIO = 0.70;

    /**
     * 목적에 따른 기회 버킷 상한(안전 버킷 하한)을 반환한다.
     *
     * @param purpose 목적 코드 (STOCK_ACCUMULATION, ONE_TIME_PURCHASE, TRAVEL, TUITION)
     * @return 안전 버킷 비율의 하한 (0.35~0.95)
     * @throws IllegalArgumentException 알 수 없는 목적인 경우
     */
    public double getSafeRatioFloor(String purpose) {
        Objects.requireNonNull(purpose, "목적은 null일 수 없습니다");

        return switch (purpose) {
            case "STOCK_ACCUMULATION" -> 0.35;
            case "ONE_TIME_PURCHASE" -> 0.50;
            case "TRAVEL" -> 0.70;
            case "TUITION" -> 0.90;
            default -> throw new IllegalArgumentException("알 수 없는 목적: " + purpose);
        };
    }

    /**
     * 사용자 입력 안전 비율이 목적 하한을 충족하는지 검증한다.
     *
     * @param purpose 목적 코드
     * @param safeRatio 사용자 입력 안전 비율 (0.0~1.0)
     * @return true 충족, false 미충족
     */
    public boolean isSafeRatioValid(String purpose, double safeRatio) {
        Objects.requireNonNull(purpose, "목적은 null일 수 없습니다");

        if (safeRatio < 0.0 || safeRatio > 1.0) {
            return false;
        }

        double floor = getSafeRatioFloor(purpose);
        return safeRatio >= floor;
    }

    /**
     * 기본 안전 비율을 반환한다.
     *
     * @return 0.70
     */
    public double getDefaultSafeRatio() {
        return DEFAULT_SAFE_RATIO;
    }
}
