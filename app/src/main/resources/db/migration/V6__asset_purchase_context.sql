-- 자산 CRUD(이슈 #13, FR-ON-04) — 매입 시점 원가 환율을 함께 저장한다.
-- 목적: 진단(M1-7)이 매입 시점 원화 환산가와 현재가를 비교할 수 있도록 매입일·환율·출처·기준일을 자산 레코드에 남긴다.
-- 모든 신규 컬럼은 nullable — 기존 데이터·KRW 자산에는 매입 환율이 없기 때문이다.
-- NFR-DT-01: 환율 수치는 출처(source)·기준일(as_of)과 함께 보존한다.

alter table holdings
    add column purchased_at            date            null,
    add column purchase_fx_rate_krw    numeric(19,4)   null,
    add column purchase_fx_rate_source varchar(64)     null,
    add column purchase_fx_rate_as_of  date            null;

alter table fx_deposits
    add column purchased_at            date            null,
    add column purchase_fx_rate_krw    numeric(19,4)   null,
    add column purchase_fx_rate_source varchar(64)     null,
    add column purchase_fx_rate_as_of  date            null;
