-- 외화 예금 테이블명을 명세 ERD(v2.0)에 맞춰 deposits → fx_deposits 로 정렬한다.
-- API 엔드포인트 경로(/deposits)와 Java 식별자(Deposit)는 그대로 두고, 물리 테이블명만 명세 기준으로 통일한다.
-- V2 는 이미 적용/체크섬이 있으므로 수정하지 않고 새 마이그레이션으로 리네임한다.
alter table deposits rename to fx_deposits;
alter index idx_deposits_owner_id rename to idx_fx_deposits_owner_id;
