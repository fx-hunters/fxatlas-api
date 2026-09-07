package com.divurve.domain.event;

/**
 * 경제 일정(econ_events) 행의 신뢰도 출처. "신뢰도 혼합 금지"(이슈 #74 제약 4) —
 * 공식 파서·AI 추출·시연용 예시 데이터를 같은 신뢰도로 섞어 저장/노출하지 않기 위해
 * 행 단위로 출처를 남긴다.
 *
 * <p>DB 컬럼 {@code econ_events.source_kind} 에는 이 enum 의 {@link #name()} 을 그대로 저장한다
 * (마이그레이션 {@code V14__econ_events.sql} 의 CHECK 제약 참고).
 */
public enum EconEventSourceKind {

    /** 구조가 일정한 공식 캘린더(연준·ECB·한국은행, FRED release API 등)를 AI 없이 파서로 처리한 결과. */
    OFFICIAL_PARSER,

    /** 구조가 없는 비정형 원문에서 LLM 이 고정 스키마(event_date/region/title/impact)로 추출한 결과. */
    AI_EXTRACTED,

    /** 해커톤 시연용 예시 데이터. 실제 수집 결과가 아니며 화면에 "시연용 예시 데이터"로 구분 표시된다. */
    DEMO_SAMPLE
}
