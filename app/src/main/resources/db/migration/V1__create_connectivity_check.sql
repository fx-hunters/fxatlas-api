-- 프론트·DB 연동 확인용 테스트 테이블 (첫 마이그레이션).
-- ddl-auto=validate 이므로 스키마는 반드시 Flyway 로 정의한다.
create table connectivity_check (
    id         bigserial    primary key,
    message    varchar(255) not null,
    created_at timestamptz  not null default now()
);
