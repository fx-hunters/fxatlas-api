package com.divurve.api.dto.system;

import java.util.List;

/**
 * 안전모드 상태 응답 (GET /system/safe-mode, 명세 3.9).
 * {@code status} 는 normal / caution / safe_mode 중 하나이며 홈 상태 라벨에 그대로 쓴다 (FR-SF-05).
 */
public record SafeModeResponse(
        boolean active,
        String status,
        List<Check> checks) {

    /** 개별 안전모드 점검 항목. */
    public record Check(String key, boolean passed) {
    }
}
