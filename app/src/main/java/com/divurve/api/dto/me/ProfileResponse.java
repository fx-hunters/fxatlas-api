package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 프로필 조회·수정 응답 (API 명세 v2 §3 {@code GET/PUT /me}).
 * {@code onboarded} 는 초기 설정 완료 여부이며 {@code users.onboarded_at} 이 NULL 이면 false 다 —
 * 클라이언트는 false 일 때 초기 설정으로 보낸다(FR-IS-01·FR-IS-07).
 */
@Schema(description = "계정 정보와 초기 설정 완료 여부")
public record ProfileResponse(
        @Schema(description = "사용자 ID", example = "3f0c2a1e-8f4b-4f6e-9a1d-2c7b5e0d1a33")
        String userId,

        @Schema(description = "이메일", example = "user@divurve.app")
        String email,

        @Schema(description = "이름", example = "김디버")
        String name,

        @Schema(description = "샘플(데모) 계정 여부", example = "false")
        boolean isDemo,

        @Schema(description = "초기 설정 완료 여부", example = "true")
        boolean onboarded,

        @Schema(description = "초기 설정 완료 시각. 미완료면 null",
                example = "2026-09-01T15:30:00Z", nullable = true)
        Instant onboardedAt) {
}
