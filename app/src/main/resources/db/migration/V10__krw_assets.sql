-- 원화 자산 (이슈 #54 / 7.3, FR-XR-01, ERD v3.0 §6.3 `krw_assets`).
-- 외화 비중의 **분모**다. 이 표가 없어서 XrayService 는 `krwAssetKrw = 0` 을 하드코딩했고,
-- 그 결과 total_asset_krw = fx_asset_krw 가 되어 fx_ratio 가 항상 1.0 으로 나왔다.
-- ddl-auto=validate 이므로 컬럼/타입은 KrwAsset 엔티티와 정확히 일치해야 한다.
--
-- ERD 와의 차이 2건 (의도된 것):
--  1) 소유자 FK 는 `user_id` 가 아니라 이 레포의 기존 관례인 `owner_id` (holdings·fx_deposits·goals 동일).
--  2) `deleted_at`(soft delete)은 두지 않는다 — holdings·fx_deposits 와 같이 하드 삭제한다.
--     soft delete 를 도입할 때 세 표를 함께 바꾼다.
-- `kind` 는 ERD ENUM `krw_asset_kind`(cash/deposit/domestic_equity/other) 를 CHECK 로 강제한다
-- (다른 도메인 컬럼과 마찬가지로 ENUM 타입 대신 varchar + CHECK 를 쓴다).

create table krw_assets (
    id         uuid         primary key default gen_random_uuid(),
    owner_id   uuid         not null references users(id),
    kind       varchar(32)  not null,
    label      varchar(255),
    amount_krw bigint       not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint chk_krw_assets_kind
        check (kind in ('cash', 'deposit', 'domestic_equity', 'other')),
    constraint chk_krw_assets_amount_krw
        check (amount_krw >= 0)
);

create index idx_krw_assets_owner_id on krw_assets(owner_id);
