package com.divurve.api.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 프로필 수정 요청 (API 명세 v2 §3 {@code PUT /me}).
 *
 * <p>이 리소스의 수정 가능한 필드는 {@code name} 하나뿐이라 부분 수정 개념이 없다 — 값을 비우면
 * {@code users.name} NOT NULL 제약을 그대로 위반해 DB 까지 내려간다. {@code @NotBlank} 로 컨트롤러
 * 경계에서 막아 400 {@code VALIDATION_FAILED} 로 응답한다(이슈 #75) — 이전에는 409
 * {@code DUPLICATE_RESOURCE} 로 잘못 나갔다.
 */
@Schema(description = "계정 정보 수정")
public record ProfileUpdateRequest(
        @Schema(description = "새 이름", example = "김디버")
        @NotBlank(message = "이름은 필수입니다.")
        String name) {
}
