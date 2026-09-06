package com.divurve.domain.port;

import java.util.List;

/**
 * 외부 데이터 공급원이 실제로 연결돼 있는지 알려주는 포트 (명세 v2 §1.2, 이슈 #57).
 *
 * <p>{@code meta.data_state} 는 지금까지 <b>어디서나 {@code mock} 으로 하드코딩</b>돼 있었다
 * ({@code ApiResponse.ok()}). 그래서 실제 API 키를 넣고 ECOS 실데이터로 계산해도 응답은 계속
 * "시연용"이라고 말했고, 클라이언트는 실수치 위에 `시연용 예시 데이터` 배지를 그렸다.
 *
 * <p>도메인은 키가 어디에 어떻게 설정되는지 모른다 — 그것은 어댑터의 사정이다. 도메인이 아는 것은
 * "지금 라이브인가"와 "무엇을 출처로 밝힐 수 있는가" 둘뿐이다.
 */
public interface DataSourceStatus {

    /**
     * 외부 데이터 공급원이 연결돼 있는지.
     *
     * @return 연결돼 있으면 {@code true} ({@code meta.data_state=live})
     */
    boolean isLive();

    /**
     * 밝힐 수 있는 데이터 출처 목록.
     *
     * <p>FR-CM-10 에 따라 <b>연결되지 않은 출처는 넣지 않는다</b> — 출처는 만들어내는 것이 아니다.
     *
     * @return 출처 이름 목록 (예 {@code ["ECOS"]}). 라이브가 아니면 빈 목록
     */
    List<String> sources();
}
