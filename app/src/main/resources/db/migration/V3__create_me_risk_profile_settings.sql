-- 마이페이지 성향·설정 (이슈 #10). RiskProfile/RiskProfileAnswer/UserSettings.
-- 사용자당 하나(owner_id unique). ddl-auto=validate 이므로 컬럼/타입은 JPA 엔티티와 정확히 일치해야 한다.

-- 투자성향 프로필 — 등급(risk_type)은 이후 집중도 기준선·버킷 비율의 입력 (FR-MY-02)
create table risk_profiles (
    id         uuid         primary key default gen_random_uuid(),
    owner_id   uuid         not null unique references users(id),
    risk_type  varchar(255) not null,
    score      integer      not null,
    created_at timestamptz  not null default now()
);
create index idx_risk_profiles_owner_id on risk_profiles(owner_id);

-- 진단 문항별 응답 이력 (RiskProfile 의 @ElementCollection — PK 없음, 소유 프로필로만 접근)
create table risk_profile_answers (
    risk_profile_id uuid         not null references risk_profiles(id),
    question_code   varchar(255) not null,
    choice          integer      not null
);
create index idx_risk_profile_answers_profile_id on risk_profile_answers(risk_profile_id);

-- 표시·거래 설정 — display_mode(설명 프로필, 판정과 분리) + 주거래 은행·환전 우대율 (FR-MY-03·FR-MY-04)
create table user_settings (
    id                uuid             primary key default gen_random_uuid(),
    owner_id          uuid             not null unique references users(id),
    default_bank_code varchar(255),
    fx_discount_ratio double precision not null,
    display_mode      varchar(255)     not null,
    created_at        timestamptz      not null default now()
);
create index idx_user_settings_owner_id on user_settings(owner_id);
