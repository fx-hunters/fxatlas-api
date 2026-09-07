-- 이슈 #74: 비정형 원문 → econ_events 구조화 추출 파이프라인의 저장 스키마 (ERD v3.0 §389~402).
-- ddl-auto=validate 이므로 컬럼/타입은 JPA 엔티티(domain/event/entity/EconEvent)와 정확히 일치해야 한다.
--
-- ERD 대비 추가 컬럼(source_url/fetched_at/source_kind)은 "신뢰도 혼합 금지" 제약(이슈 #74 제약 4) 때문이다.
-- 공식 파서·AI 추출·시연용 예시 데이터를 같은 신뢰도로 섞지 않기 위해 행 단위로 출처를 남긴다.
create table econ_events (
    id          uuid          primary key default gen_random_uuid(),
    event_date  date          not null,
    region      text          not null,
    title       text          not null,
    impact      smallint      not null,
    -- 원문 URL. 공식 파서 소스는 캘린더 페이지, AI 추출 소스는 크롤링한 뉴스 원문 URL을 담는다.
    -- 시연용 예시 데이터(demo_sample)는 원문이 없으므로 NULL을 허용한다.
    source_url  text,
    fetched_at  timestamptz   not null,
    -- EconEventSourceKind(자바 enum) 의 name() 을 대문자 그대로 저장한다.
    --   OFFICIAL_PARSER -> 'OFFICIAL_PARSER' (구조화된 공식 캘린더, AI 미사용)
    --   AI_EXTRACTED    -> 'AI_EXTRACTED'    (비정형 원문에서 LLM 이 고정 스키마로 추출)
    --   DEMO_SAMPLE     -> 'DEMO_SAMPLE'     (해커톤 시연용 예시 데이터, 실데이터 아님)
    source_kind text          not null,
    constraint ck_events_impact check (impact between 1 and 3),
    constraint ck_events_source_kind check (source_kind in ('OFFICIAL_PARSER', 'AI_EXTRACTED', 'DEMO_SAMPLE')),
    -- 같은 배치가 재실행되거나 여러 소스가 같은 사건을 다시 적재하는 것을 막는다 (이슈 #74 "중복 적재 방지").
    constraint uq_events_date_region_title unique (event_date, region, title)
);
create index idx_events_date on econ_events (event_date);

-- 이벤트 ↔ 통화쌍 매핑 (ERD econ_event_pairs). USD 화면에 무관한 지역 지표가 뜨는 것을 막는다.
--
-- ⚠️ ERD 는 pair_code -> currency_pairs(pair_code) FK 를 건다. 이 레포에는 아직 currency_pairs
--    테이블이 없다(V1~V12 확인 완료, ERD 구축 순서 1단계 미착수). FK 를 걸면 이 마이그레이션이
--    실패하므로 지금은 FK 없이 컬럼 타입만 ERD 와 맞춘다. currency_pairs 도입 시 별도 마이그레이션에서
--    FK 를 추가한다 (V11 stress_test_runs 의 portfolio_snapshots FK 보류와 같은 방식).
create table econ_event_pairs (
    event_id  uuid      not null references econ_events(id),
    pair_code char(6)   not null,
    primary key (event_id, pair_code)
);
