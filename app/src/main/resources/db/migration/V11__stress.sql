-- 스트레스 테스트 (ERD v3.0 §F, API 명세 v2 §5.9, 요구사항 FR-ST-01~05).
-- v1 의 POST /xray/stress 는 저장 없이 통화별 환율 충격만 계산했다. v2 는 시나리오 마스터를 두고
-- 실행 결과를 남긴다 — "사용자에게 노출된 계산 근거"이므로 삭제하지 않는다(ERD §12, FR-ST-05).
-- ddl-auto=validate 이므로 컬럼/타입은 JPA 엔티티(domain/stress/entity)와 정확히 일치해야 한다.

-- 시나리오 마스터. 충격률은 가정값이며 예측이 아니다(FR-ST-04).
-- equity_shock_pct: 해외주식 평가액에 먼저 적용하는 주가 충격 (음수 = 하락)
-- fx_shock_pct:     주가 충격 반영 후 외화자산 전체에 적용하는 환율 충격
--                   (양수 = USD/KRW 상승 = 원화 약세 = 외화 평가액 증가, FR-CM-05)
create table stress_scenarios (
    scenario_code    text     primary key,
    name_ko          text     not null,
    equity_shock_pct numeric(6,4) not null,
    fx_shock_pct     numeric(6,4) not null,
    reference_event  text,
    assumption_note  text,
    is_default       boolean  not null default false,
    sort_order       smallint not null default 99
);

-- 기본 2종 (요구사항 §4.8 "주가 하락+원화 약세 / 주가 하락+원화 강세").
-- 충격률 -0.20 / ±0.10 은 API 명세 §5.9 검산 예시에서 온 값이다.
insert into stress_scenarios
    (scenario_code, name_ko, equity_shock_pct, fx_shock_pct, reference_event, assumption_note, is_default, sort_order)
values
    ('equity_down_krw_weak', '주가 하락 + 원화 약세', -0.2000, 0.1000,
     '2020년 3월 변동성 급등 참고',
     '해외주식 평가액에 주가 충격을 먼저 적용한 뒤 외화자산 전체에 환율 충격을 적용합니다.',
     true, 1),
    ('equity_down_krw_strong', '주가 하락 + 원화 강세', -0.2000, -0.1000,
     '2008년 금융위기 이후 원화 반등 국면 참고',
     '해외주식 평가액에 주가 충격을 먼저 적용한 뒤 외화자산 전체에 환율 충격을 적용합니다.',
     true, 2);

-- 실행 이력. 충격률은 실행 시점 스냅샷이라 이후 마스터가 바뀌어도 과거 결과가 그대로 재현된다.
-- 효과 3컬럼(주가/환율/총액)은 요구사항 §4.8 "주가·환율·총 평가금액 효과 분리"를 그대로 담는다.
-- equity_effect_krw + fx_effect_krw = total_effect_krw 가 항상 성립한다(적용 순서 고정).
--
-- ⚠️ ERD 는 (user_id, snapshot_date) → portfolio_snapshots FK 를 건다. portfolio_snapshots 는
--    아직 만들어지지 않은 테이블(ERD 구축 순서 8단계)이라 여기서는 FK 를 걸지 않고 컬럼만 둔다.
--    portfolio_snapshots 도입 시 별도 마이그레이션에서 FK 를 추가한다.
create table stress_test_runs (
    id                uuid         primary key default gen_random_uuid(),
    user_id           uuid         not null references users(id),
    scenario_code     text         not null references stress_scenarios(scenario_code),
    base_date         date         not null,
    equity_shock_pct  numeric(6,4) not null,
    fx_shock_pct      numeric(6,4) not null,
    equity_effect_krw numeric(18,0) not null,
    fx_effect_krw     numeric(18,0) not null,
    total_effect_krw  numeric(18,0) not null,
    snapshot_date     date,
    created_at        timestamptz  not null default now()
);
create index idx_stress_user on stress_test_runs (user_id, created_at desc);
