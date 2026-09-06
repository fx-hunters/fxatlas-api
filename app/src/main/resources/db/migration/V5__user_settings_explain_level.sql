-- 설정 표시 프로필을 ERD v3.0에 맞춘다: display_mode → explain_level(설명 선호 3단계) + explain_domain(익숙한 분야).
-- V3 는 체크섬 때문에 수정하지 않고 새 마이그레이션으로 컬럼을 재정렬한다. ddl-auto=validate 이므로 엔티티와 정확히 일치해야 한다.
alter table user_settings rename column display_mode to explain_level;
alter table user_settings add column explain_domain varchar(255) not null default 'plain';
