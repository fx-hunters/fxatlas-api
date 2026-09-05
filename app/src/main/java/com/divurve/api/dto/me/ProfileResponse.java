package com.divurve.api.dto.me;

/** 프로필 조회 응답 (GET /me). */
public record ProfileResponse(
        String userId,
        String email,
        String name,
        boolean isDemo) {
}
