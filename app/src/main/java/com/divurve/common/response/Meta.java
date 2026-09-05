package com.divurve.common.response;

import java.time.Instant;

/**
 * 모든 API 응답에 함께 실리는 메타 정보 (문서 6장 — data + meta 래핑).
 * 지금은 응답 생성 시각만 담지만, 페이지네이션·요청 ID 등이 여기에 추가된다.
 */
public record Meta(Instant timestamp) {

    /** 현재 시각으로 메타를 생성한다. */
    public static Meta now() {
        return new Meta(Instant.now());
    }
}
