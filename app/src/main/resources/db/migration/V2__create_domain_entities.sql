-- 도메인 기반 계층 (이슈 #8). User/Holding/Deposit/Goal/Plan/PlanStep.
-- PK 는 UUID(gen_random_uuid), 소유자 FK 로 데이터 격리(NFR-SE-03).
-- ddl-auto=validate 이므로 컬럼/타입은 JPA 엔티티와 정확히 일치해야 한다.

-- gen_random_uuid() 제공 (PG13+ 는 내장이나 구버전 호환 위해 명시)
create extension if not exists pgcrypto;

-- 사용자 — 모든 자산·목표 데이터의 소유자 루트
create table users (
    id         uuid         primary key default gen_random_uuid(),
    email      varchar(255) not null unique,
    name       varchar(255) not null,
    is_demo    boolean      not null,
    created_at timestamptz  not null default now()
);

-- 보유 종목
create table holdings (
    id            uuid             primary key default gen_random_uuid(),
    owner_id      uuid             not null references users(id),
    ticker        varchar(255)     not null,
    currency_code varchar(255)     not null,
    quantity      double precision not null,
    avg_price     double precision not null,
    created_at    timestamptz      not null default now()
);
create index idx_holdings_owner_id on holdings(owner_id);

-- 외화 예금 (외화 금액 소수 4자리, 명세 1.4)
create table deposits (
    id            uuid          primary key default gen_random_uuid(),
    owner_id      uuid          not null references users(id),
    currency_code varchar(255)  not null,
    amount        numeric(19,4) not null,
    created_at    timestamptz   not null default now()
);
create index idx_deposits_owner_id on deposits(owner_id);

-- 목표
create table goals (
    id                   uuid             primary key default gen_random_uuid(),
    owner_id             uuid             not null references users(id),
    name                 varchar(255)     not null,
    kind                 varchar(255)     not null,
    purpose              varchar(255)     not null,
    currency_code        varchar(255)     not null,
    target_amount        double precision not null,
    target_date          date,
    recur_interval       varchar(255),
    budget_amount        bigint           not null,
    budget_currency_code varchar(255),
    budget_period        varchar(255),
    is_speculative       boolean          not null,
    status               varchar(255)     not null,
    created_at           timestamptz      not null default now()
);
create index idx_goals_owner_id on goals(owner_id);

-- 계획 (목표별 버전, 활성 버전은 하나)
create table plans (
    id                       uuid             primary key default gen_random_uuid(),
    goal_id                  uuid             not null references goals(id),
    version                  integer          not null,
    is_active                boolean          not null,
    reason                   varchar(255),
    safe_ratio               double precision not null,
    split_count              integer          not null,
    opportunity_amount       double precision not null,
    opportunity_trigger_rate double precision not null,
    created_at               timestamptz      not null default now()
);
create index idx_plans_goal_id on plans(goal_id);

-- 계획 회차
create table plan_steps (
    id              uuid             primary key default gen_random_uuid(),
    plan_id         uuid             not null references plans(id),
    seq             integer          not null,
    scheduled_date  date,
    amount          double precision not null,
    executed_amount double precision not null,
    status          varchar(255)     not null,
    created_at      timestamptz      not null default now()
);
create index idx_plan_steps_plan_id on plan_steps(plan_id);
