package com.divurve.common.response;

import java.time.Instant;

/**
 * 전역 API 응답 래퍼 (API 명세 v2 §1.2). 모든 응답은 {@code data} + {@code meta} 로 감싼다.
 * 프론트는 항상 동일한 봉투(envelope)를 받으므로 파싱 규칙이 하나로 통일된다.
 *
 * @param <T> 실제 페이로드 타입
 */
public record ApiResponse<T>(T data, Meta meta) {

    /**
     * 페이로드만 넘기면 기본 메타를 채워 감싼다.
     *
     * <p>현재 서비스 데이터는 전부 Mock/상수이므로 기본값은 {@code data_state=mock} + 빈 {@code sources} 다
     * (FR-CM-02, FR-CM-10). 실데이터 연동 후에는 해당 엔드포인트가 {@link Meta#live(Instant, java.util.List)}
     * 로 만든 메타를 {@link #of(Object, Meta)} 에 직접 넘긴다. {@code is_demo} 는 응답 직전에
     * {@code MetaDemoFlagAdvice} 가 현재 요청 주체를 보고 채운다(FR-IS-09).
     */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Meta.mock(Instant.now()));
    }

    /** 메타를 직접 지정해 감싼다. */
    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }

    /** 메타만 교체한 새 응답을 만든다. 응답 직전 공통 후처리(예: {@code is_demo} 주입)가 쓴다. */
    public ApiResponse<T> withMeta(Meta meta) {
        return new ApiResponse<>(data, meta);
    }
}
