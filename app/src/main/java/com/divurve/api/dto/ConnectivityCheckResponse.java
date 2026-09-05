package com.divurve.api.dto;

import com.divurve.domain.connectivity.entity.ConnectivityCheck;
import java.time.Instant;

/**
 * 테스트 행 응답 DTO. camelCase 로 작성하면 전역 Jackson SNAKE_CASE 전략이
 * {@code created_at} 등으로 직렬화한다 (DB 컬럼명 = API 필드명, 문서 5장).
 */
public record ConnectivityCheckResponse(Long id, String message, Instant createdAt) {

    /** 엔티티를 응답 DTO 로 변환한다. */
    public static ConnectivityCheckResponse from(ConnectivityCheck entity) {
        return new ConnectivityCheckResponse(entity.getId(), entity.getMessage(), entity.getCreatedAt());
    }
}
