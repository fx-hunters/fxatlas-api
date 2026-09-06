-- 진단(FR-DG)·초기 설정(FR-IS)·마이페이지(FR-MY) v2 정합 (API 명세 v2 §3·§5.1·§5.2, ERD v3.0 §0·§4.B).
-- ddl-auto=validate 이므로 컬럼/타입은 JPA 엔티티와 정확히 일치해야 한다.

-- 1) users.onboarded_at — NULL 이면 초기 설정으로 보낸다. POST /me/onboarding/complete 가 기록한다.
--    (ERD v3.0 §0 users, §4.B "NULL이면 온보딩 리다이렉트" / 명세 §3 FR-IS-01·FR-IS-05·FR-IS-07)
alter table users
    add column onboarded_at timestamptz;

-- 2) risk_profiles — 간편(Q1~Q3)/상세(Q4~Q6) 진단의 상태·근거·기준선 (ERD v3.0 risk_profiles).
--    grade(risk_type)·score 는 ERD 상 NOT NULL 이지만, 명세 §5.1 이 "미응답이 있으면 유형을 만들지 않는다"(FR-DG-02)
--    + "부분 제출 허용"(FR-DG-04)을 동시에 요구한다. 응답만 저장된 재개 대기 행이 존재해야 하므로 NULL 을 허용한다.
alter table risk_profiles
    alter column risk_type drop not null;
alter table risk_profiles
    alter column score drop not null;

alter table risk_profiles
    add column status                  varchar(255) not null default 'not_measured',
    add column concentration_threshold numeric(5, 4),
    add column safe_ratio_adjust       numeric(5, 4),
    add column answers                 jsonb        not null default '{}'::jsonb,
    add column detail_answers          jsonb,
    add column detail_progress         jsonb,
    add column diagnosed_on            date,
    add column is_manual               boolean      not null default false,
    add column updated_at              timestamptz  not null default now();

-- score 범위 제약 (ERD ck_score). NULL(미측정)은 CHECK 를 통과한다.
alter table risk_profiles
    add constraint ck_score check (score between 0 and 9);

-- 기존 행 보정 — V3 로 만들어진 행은 간편 진단이 끝난 상태다.
update risk_profiles
set status        = 'simple_done',
    diagnosed_on  = created_at::date
where risk_type is not null;

-- 진단 응답은 ERD 대로 risk_profiles.answers(JSONB) 단일 컬럼으로 모은다 —
-- 정규화 테이블(V3)은 문항 선택지 코드(A~D)를 담지 못해 명세 §5.1 simple.answers 를 만들 수 없다.
drop table if exists risk_profile_answers;

-- 3) user_settings 알림 스위치 5종 (ERD v3.0 user_settings). PUT /me/notifications 를 /me/settings 로 흡수하면서
--    V8 의 v1 알림 4종을 ERD 이름으로 정렬한다 (명세 §3 마이페이지 표).
alter table user_settings
    rename column exchange_schedule_reminder to notify_step_due;
alter table user_settings
    rename column review_required_alert to notify_regime_shift;
alter table user_settings
    rename column deadline_approach_alert to notify_deadline_near;
alter table user_settings
    rename column bucket_entry_alert to notify_target_zone;

-- ERD 기본값: notify_target_zone 만 FALSE, 나머지는 TRUE.
alter table user_settings
    alter column notify_target_zone set default false;
alter table user_settings
    add column notify_concentration boolean not null default true;
