-- 회원가입·로그인 본구현 (이슈 #22).
-- Users 테이블에 password_hash, onboarding_purpose 컬럼 추가.
-- password_hash: BCrypt 해시 저장, 데모 유저는 null (NFR-SE-01).
-- onboarding_purpose: 온보딩 목적 (OVERSEAS_INVESTMENT 또는 FOREIGN_CURRENCY_GOAL), 데모 유저는 null.

alter table users
add column password_hash varchar(255),
add column onboarding_purpose varchar(255);
