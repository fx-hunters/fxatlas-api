package com.divurve.api.dto.system;

import java.util.List;

/**
 * 안전모드 상태 응답 (GET /system/safe-mode, 명세 3.9).
 * {@code status} 는 normal / caution / safe_mode 중 하나이며 홈 상태 라벨에 그대로 쓴다 (FR-SF-05).
 *
 * @param active 안전모드 활성화 여부
 * @param status 상태 라벨 (normal / caution / safe_mode)
 * @param checks 개별 조건 평가 결과
 */
public record SafeModeResponse(
        boolean active,
        String status,
        List<Check> checks) {

    /**
     * 개별 안전모드 점검 항목.
     *
     * @param key 조건 식별자 (예: data_staleness)
     * @param passed true면 정상, false면 위반
     * @param reason 위반 시 사유 (선택 사항)
     */
    public record Check(String key, boolean passed, String reason) {
    }
}
