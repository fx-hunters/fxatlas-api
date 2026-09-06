package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;

/** 프로필 수정 요청 (API 명세 v2 §3 {@code PUT /me}). */
@Schema(description = "계정 정보 수정")
public record ProfileUpdateRequest(
        @Schema(description = "새 이름", example = "김디버")
        String name) {
}
