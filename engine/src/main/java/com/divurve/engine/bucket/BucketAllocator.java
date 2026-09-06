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
