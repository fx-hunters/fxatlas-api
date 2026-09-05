package com.divurve.common.response;

/**
 * 전역 API 응답 래퍼 (문서 6장). 모든 응답은 {@code data} + {@code meta} 로 감싼다.
 * 프론트는 항상 동일한 봉투(envelope)를 받으므로 파싱 규칙이 하나로 통일된다.
 *
 * @param <T> 실제 페이로드 타입
 */
public record ApiResponse<T>(T data, Meta meta) {

    /** 페이로드만 넘기면 메타(현재 시각)를 자동으로 채워 감싼다. */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Meta.now());
    }

    /** 메타를 직접 지정해 감싼다. */
    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }
}
