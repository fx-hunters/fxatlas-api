# ERD 간략 설명 (데이터 모델 v3.0)

> 원본: Notion「ERD · 데이터 모델 v3.0」(PostgreSQL 15, DDL 정본) — 경제 배경 없이 구현하는 개발자 대상 설명본.
> ERD v3.0은 **7개 도메인 32개 테이블**. 시각은 전부 UTC로 저장하고 표시 시 Asia/Seoul로 변환한다. 시나리오: 사용자 `희찬`이 2027-03 유럽여행에 €3,000이 필요해 목표를 만들고 예산 500만원으로 6회 분할, 3회차까지 환전한 상태. 해외주식 `VOO` 12.5주 보유.

## 0. DDL
```postgresql
-- =====================================================================
-- FX ATLAS — ERD v3.0 DDL (PostgreSQL 15)
-- 문자열 UTF-8 · 시각은 전부 UTC로 저장, 표시 시 Asia/Seoul 변환
-- gen_random_uuid()는 PostgreSQL 13+ 코어 내장 함수를 사용한다
-- =====================================================================

-- ---------------------------------------------------------------------
-- ENUM 타입
-- ---------------------------------------------------------------------

CREATE TYPE usd_pair_side AS ENUM ('self', 'base', 'quote', 'none');
CREATE TYPE fx_channel AS ENUM ('tt', 'cash');
CREATE TYPE rate_type AS ENUM ('mid', 'tt_buy', 'tt_sell', 'cash_buy', 'cash_sell');
CREATE TYPE security_type AS ENUM ('stock', 'etf', 'adr', 'reit');
CREATE TYPE price_source AS ENUM ('yahoo', 'alphavantage', 'manual');
CREATE TYPE krw_asset_kind AS ENUM ('cash', 'deposit', 'domestic_equity', 'other');
CREATE TYPE risk_grade AS ENUM ('stable', 'balanced', 'aggressive', 'challenging');
CREATE TYPE explain_level AS ENUM ('simple', 'standard', 'detailed');
CREATE TYPE explain_domain AS ENUM ('finance', 'dev', 'marketing', 'plain');
CREATE TYPE diagnosis_status AS ENUM ('not_measured', 'simple_done', 'detail_done');
CREATE TYPE goal_kind AS ENUM ('recurring', 'deadline');
CREATE TYPE goal_purpose AS ENUM ('invest', 'once', 'travel', 'tuition');
CREATE TYPE recur_interval AS ENUM ('weekly', 'monthly', 'quarterly');
CREATE TYPE budget_period AS ENUM ('monthly', 'total');
CREATE TYPE goal_status AS ENUM ('active', 'paused', 'completed', 'cancelled');
CREATE TYPE plan_reason AS ENUM ('initial', 'skipped', 'safe_mode', 'user_edit', 'replan', 'rollover');
CREATE TYPE step_status AS ENUM ('pending', 'done', 'partial', 'skipped', 'forced');
CREATE TYPE entry_method AS ENUM ('manual', 'csv_import', 'open_banking');
CREATE TYPE vol_regime AS ENUM ('calm', 'normal', 'elevated', 'stress');
CREATE TYPE factor_category AS ENUM ('rate_diff', 'risk_sentiment', 'flow', 'policy', 'technical');
CREATE TYPE notification_type AS ENUM ('step_due', 'regime_shift', 'deadline_near', 'target_zone', 'safe_mode', 'concentration');
CREATE TYPE audit_action AS ENUM ('plan_created', 'plan_recalculated', 'ai_explained', 'warning_shown', 'forecast_served', 'data_stale');

-- ---------------------------------------------------------------------
-- A. 마스터 · 기준정보
-- ---------------------------------------------------------------------

CREATE TABLE currencies (
  currency_code     CHAR(3) PRIMARY KEY,
  name_ko           TEXT NOT NULL,
  symbol            TEXT NOT NULL,
  minor_units       SMALLINT NOT NULL,
  quote_unit        SMALLINT NOT NULL DEFAULT 1,
  usd_side          usd_pair_side NOT NULL,
  is_home_currency  BOOLEAN NOT NULL DEFAULT FALSE,
  is_supported      BOOLEAN NOT NULL DEFAULT FALSE,
  support_note      TEXT,
  color_token       TEXT,
  sort_order        SMALLINT NOT NULL DEFAULT 99,
  CONSTRAINT ck_minor CHECK (minor_units BETWEEN 0 AND 4),
  CONSTRAINT ck_qunit CHECK (quote_unit IN (1, 100))
);
CREATE UNIQUE INDEX uq_home_currency ON currencies (is_home_currency) WHERE is_home_currency;

CREATE TABLE currency_pairs (
  pair_code             CHAR(6) PRIMARY KEY,
  base_currency_code    CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  quote_currency_code   CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  is_stored             BOOLEAN NOT NULL,
  derive_via_pair_code  CHAR(6) REFERENCES currency_pairs(pair_code),
  CONSTRAINT ck_derive CHECK (
    (is_stored AND derive_via_pair_code IS NULL) OR
    (NOT is_stored AND derive_via_pair_code IS NOT NULL)
  )
);

CREATE TABLE banks (
  bank_code TEXT PRIMARY KEY,
  name_ko   TEXT NOT NULL
);

CREATE TABLE bank_fx_terms (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bank_code       TEXT NOT NULL REFERENCES banks(bank_code),
  currency_code   CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  channel         fx_channel NOT NULL,
  list_spread     NUMERIC(6,5) NOT NULL,
  fixed_fee_krw   INTEGER NOT NULL DEFAULT 0,
  min_amount      NUMERIC(18,4),
  effective_from  DATE NOT NULL,
  CONSTRAINT uq_bank_terms UNIQUE (bank_code, currency_code, channel, effective_from)
);

CREATE TABLE securities (
  ticker        TEXT PRIMARY KEY,
  name          TEXT,
  currency_code CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  exchange      TEXT,
  asset_type    security_type NOT NULL,
  is_supported  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE factors (
  factor_code   TEXT PRIMARY KEY,
  name_ko       TEXT NOT NULL,
  category      factor_category NOT NULL,
  source_series TEXT
);

CREATE TABLE stress_scenarios (
  scenario_code     TEXT PRIMARY KEY,
  name_ko           TEXT NOT NULL,
  equity_shock_pct  NUMERIC(6,4) NOT NULL,
  fx_shock_pct      NUMERIC(6,4) NOT NULL,
  reference_event   TEXT,
  assumption_note   TEXT,
  is_default        BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order        SMALLINT NOT NULL DEFAULT 99
);

-- ---------------------------------------------------------------------
-- B. 사용자
-- ---------------------------------------------------------------------

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT,
  onboarded_at  TIMESTAMPTZ,
  is_demo       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);

CREATE TABLE user_settings (
  user_id               UUID PRIMARY KEY REFERENCES users(id),
  default_bank_code     TEXT REFERENCES banks(bank_code),
  fx_discount_ratio     NUMERIC(5,4) NOT NULL DEFAULT 0,
  explain_level         explain_level NOT NULL DEFAULT 'simple',
  explain_domain        explain_domain NOT NULL DEFAULT 'plain',
  notify_step_due       BOOLEAN NOT NULL DEFAULT TRUE,
  notify_regime_shift   BOOLEAN NOT NULL DEFAULT TRUE,
  notify_deadline_near  BOOLEAN NOT NULL DEFAULT TRUE,
  notify_target_zone    BOOLEAN NOT NULL DEFAULT FALSE,
  notify_concentration  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE user_banks (
  user_id     UUID NOT NULL REFERENCES users(id),
  bank_code   TEXT NOT NULL REFERENCES banks(bank_code),
  is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
  memo        TEXT,
  PRIMARY KEY (user_id, bank_code)
);

CREATE TABLE risk_profiles (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                  UUID NOT NULL REFERENCES users(id),
  grade                    risk_grade NOT NULL,
  score                    SMALLINT NOT NULL,
  concentration_threshold  NUMERIC(5,4) NOT NULL,
  safe_ratio_adjust        NUMERIC(5,4) NOT NULL,
  answers                  JSONB NOT NULL,
  detail_answers           JSONB,
  detail_progress          JSONB,
  status                   diagnosis_status NOT NULL DEFAULT 'simple_done',
  is_manual                BOOLEAN NOT NULL DEFAULT FALSE,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_score CHECK (score BETWEEN 0 AND 9)
);
CREATE INDEX idx_risk_latest ON risk_profiles (user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- C. 자산
-- ---------------------------------------------------------------------

CREATE TABLE holdings (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL REFERENCES users(id),
  ticker                TEXT NOT NULL REFERENCES securities(ticker),
  quantity              NUMERIC(18,4) NOT NULL,
  avg_cost              NUMERIC(18,4) NOT NULL,
  purchased_on          DATE NOT NULL,
  purchase_fx_rate      NUMERIC(14,6),
  fx_rate_entry_method  TEXT NOT NULL DEFAULT 'auto',
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at            TIMESTAMPTZ
);

CREATE TABLE fx_deposits (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID NOT NULL REFERENCES users(id),
  currency_code      CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  bank_code          TEXT REFERENCES banks(bank_code),
  balance            NUMERIC(18,4) NOT NULL DEFAULT 0,
  avg_acquired_rate  NUMERIC(14,6),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_deposit UNIQUE (user_id, currency_code, bank_code)
);

CREATE TABLE krw_assets (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id),
  kind        krw_asset_kind NOT NULL,
  label       TEXT,
  amount_krw  NUMERIC(18,0) NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMPTZ
);

CREATE TABLE portfolio_snapshots (
  user_id        UUID NOT NULL REFERENCES users(id),
  snapshot_date  DATE NOT NULL,
  total_krw      NUMERIC(18,0) NOT NULL,
  fx_krw         NUMERIC(18,0) NOT NULL,
  krw_only       NUMERIC(18,0) NOT NULL,
  exposure       JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, snapshot_date)
);

-- ---------------------------------------------------------------------
-- D. 목표 · 계획
-- ---------------------------------------------------------------------

CREATE TABLE goals (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                UUID NOT NULL REFERENCES users(id),
  name                   TEXT NOT NULL,
  kind                   goal_kind NOT NULL,
  purpose                goal_purpose NOT NULL,
  currency_code          CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  target_amount          NUMERIC(18,4) NOT NULL,
  target_date            DATE NOT NULL,
  recur_interval         recur_interval,
  budget_amount          NUMERIC(18,0) NOT NULL,
  budget_currency_code   CHAR(3) NOT NULL DEFAULT 'KRW' REFERENCES currencies(currency_code),
  budget_period          budget_period NOT NULL,
  is_speculative         BOOLEAN NOT NULL DEFAULT FALSE,
  status                 goal_status NOT NULL DEFAULT 'active',
  priority               SMALLINT NOT NULL DEFAULT 1,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at             TIMESTAMPTZ
);
CREATE INDEX idx_goals_user_active ON goals (user_id, status) WHERE deleted_at IS NULL;

CREATE TABLE plans (
  id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  goal_id                   UUID NOT NULL REFERENCES goals(id),
  version                   INTEGER NOT NULL,
  reason                    plan_reason NOT NULL,
  reason_detail             TEXT,
  risk_profile_id           UUID REFERENCES risk_profiles(id),
  held_snapshot             NUMERIC(18,4) NOT NULL,
  rate_snapshot             NUMERIC(14,6) NOT NULL,
  sigma_snapshot            NUMERIC(8,5) NOT NULL,
  spread_snapshot           NUMERIC(6,5) NOT NULL,
  fixed_fee_snapshot        INTEGER NOT NULL,
  weekly_budget_krw         NUMERIC(18,0) NOT NULL,
  safe_ratio                NUMERIC(5,4),
  split_count               SMALLINT,
  weeks                     SMALLINT,
  opportunity_amount        NUMERIC(18,4) NOT NULL DEFAULT 0,
  opportunity_trigger_rate  NUMERIC(14,6),
  achieve_prob              NUMERIC(5,4),
  entry_sigma               NUMERIC(8,5),
  worst5_rate               NUMERIC(14,6),
  total_fee_krw             NUMERIC(18,0) NOT NULL DEFAULT 0,
  is_active                 BOOLEAN NOT NULL DEFAULT TRUE,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_plans_version UNIQUE (goal_id, version),
  CONSTRAINT ck_safe_ratio CHECK (safe_ratio IS NULL OR safe_ratio BETWEEN 0 AND 1)
);
CREATE UNIQUE INDEX uq_plans_active ON plans (goal_id) WHERE is_active;

CREATE TABLE plan_steps (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id             UUID NOT NULL REFERENCES plans(id),
  seq                 SMALLINT NOT NULL,
  scheduled_date      DATE NOT NULL,
  amount              NUMERIC(18,4) NOT NULL,
  executed_amount     NUMERIC(18,4) NOT NULL DEFAULT 0,
  is_final_safe_date  BOOLEAN NOT NULL DEFAULT FALSE,
  status              step_status NOT NULL DEFAULT 'pending',
  CONSTRAINT uq_steps_seq UNIQUE (plan_id, seq),
  CONSTRAINT ck_exec_amt CHECK (executed_amount <= amount)
);
CREATE INDEX idx_steps_due ON plan_steps (scheduled_date, status) WHERE status = 'pending';

CREATE TABLE executions (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  goal_id        UUID NOT NULL REFERENCES goals(id),
  step_id        UUID REFERENCES plan_steps(id),
  currency_code  CHAR(3) NOT NULL REFERENCES currencies(currency_code),
  bank_code      TEXT REFERENCES banks(bank_code),
  channel        fx_channel NOT NULL DEFAULT 'tt',
  executed_at    TIMESTAMPTZ NOT NULL,
  amount         NUMERIC(18,4) NOT NULL,
  applied_rate   NUMERIC(14,6) NOT NULL,
  fee_krw        NUMERIC(18,0) NOT NULL DEFAULT 0,
  krw_paid       NUMERIC(18,0) NOT NULL,
  entry_method   entry_method NOT NULL DEFAULT 'manual',
  note           TEXT
);
CREATE INDEX idx_exec_goal ON executions (goal_id, executed_at DESC);

-- ---------------------------------------------------------------------
-- E. 시장 데이터
-- ---------------------------------------------------------------------

CREATE TABLE fx_rates (
  pair_code    CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  quote_date   DATE NOT NULL,
  rate_type    rate_type NOT NULL,
  rate         NUMERIC(14,6) NOT NULL,
  data_source  TEXT NOT NULL,
  fetched_at   TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (pair_code, quote_date, rate_type)
);

CREATE TABLE fx_stats (
  pair_code          CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  quote_date         DATE NOT NULL,
  vol_7d             NUMERIC(8,5),
  vol_30d            NUMERIC(8,5) NOT NULL,
  vol_90d            NUMERIC(8,5),
  vol_percentile_5y  NUMERIC(5,4) NOT NULL,
  regime             vol_regime NOT NULL,
  PRIMARY KEY (pair_code, quote_date)
);

CREATE TABLE fx_correlations (
  pair_code_a  CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  pair_code_b  CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  window_days  SMALLINT NOT NULL,
  as_of        DATE NOT NULL,
  rho          NUMERIC(6,5) NOT NULL,
  PRIMARY KEY (pair_code_a, pair_code_b, window_days, as_of),
  CONSTRAINT ck_corr_order CHECK (pair_code_a < pair_code_b),
  CONSTRAINT ck_corr_range CHECK (rho BETWEEN -1 AND 1)
);

CREATE TABLE security_prices (
  ticker       TEXT NOT NULL REFERENCES securities(ticker),
  price_date   DATE NOT NULL,
  close_price  NUMERIC(18,4) NOT NULL,
  data_source  price_source NOT NULL,
  fetched_at   TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (ticker, price_date)
);
CREATE INDEX idx_price_latest ON security_prices (ticker, price_date DESC);

CREATE TABLE forecasts (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pair_code      CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  horizon_days   SMALLINT NOT NULL,
  base_date      DATE NOT NULL,
  base_rate      NUMERIC(14,6) NOT NULL,
  model_path     JSONB,
  p50_lo         NUMERIC(14,6) NOT NULL,
  p50_hi         NUMERIC(14,6) NOT NULL,
  p80_lo         NUMERIC(14,6) NOT NULL,
  p80_hi         NUMERIC(14,6) NOT NULL,
  model_version  TEXT NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_forecast UNIQUE (pair_code, base_date, horizon_days)
);

CREATE TABLE forecast_factors (
  forecast_id   UUID NOT NULL REFERENCES forecasts(id),
  factor_code   TEXT NOT NULL REFERENCES factors(factor_code),
  direction     SMALLINT NOT NULL,
  strength      NUMERIC(4,3) NOT NULL,
  latest_value  NUMERIC(18,6),
  PRIMARY KEY (forecast_id, factor_code)
);

CREATE TABLE model_runs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pair_code       CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  horizon_days    SMALLINT NOT NULL,
  hit_rate        NUMERIC(5,4),
  mae             NUMERIC(8,5),
  coverage_80     NUMERIC(5,4) NOT NULL,
  avg_width       NUMERIC(8,5) NOT NULL,
  rw_improvement  NUMERIC(6,4) NOT NULL,
  evaluated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE econ_events (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_date  DATE NOT NULL,
  region      TEXT NOT NULL,
  title       TEXT NOT NULL,
  impact      SMALLINT NOT NULL
);
CREATE INDEX idx_events_date ON econ_events (event_date);

CREATE TABLE econ_event_pairs (
  event_id   UUID NOT NULL REFERENCES econ_events(id),
  pair_code  CHAR(6) NOT NULL REFERENCES currency_pairs(pair_code),
  PRIMARY KEY (event_id, pair_code)
);

CREATE TABLE macro_series (
  series_code  TEXT NOT NULL,
  obs_date     DATE NOT NULL,
  value        NUMERIC(18,6) NOT NULL,
  data_source  TEXT NOT NULL,
  PRIMARY KEY (series_code, obs_date)
);

-- ---------------------------------------------------------------------
-- F. Stress Test
-- ---------------------------------------------------------------------

CREATE TABLE stress_test_runs (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID NOT NULL REFERENCES users(id),
  scenario_code      TEXT NOT NULL REFERENCES stress_scenarios(scenario_code),
  base_date          DATE NOT NULL,
  equity_shock_pct   NUMERIC(6,4) NOT NULL,
  fx_shock_pct       NUMERIC(6,4) NOT NULL,
  equity_effect_krw  NUMERIC(18,0) NOT NULL,
  fx_effect_krw      NUMERIC(18,0) NOT NULL,
  total_effect_krw   NUMERIC(18,0) NOT NULL,
  snapshot_date      DATE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (user_id, snapshot_date) REFERENCES portfolio_snapshots(user_id, snapshot_date)
);
CREATE INDEX idx_stress_user ON stress_test_runs (user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- G. 지원 테이블
-- ---------------------------------------------------------------------

CREATE TABLE notifications (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id),
  goal_id     UUID REFERENCES goals(id),
  type        notification_type NOT NULL,
  dedup_key   TEXT NOT NULL,
  title       TEXT NOT NULL,
  body        TEXT NOT NULL,
  sent_at     TIMESTAMPTZ,
  read_at     TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_notif_dedup UNIQUE (user_id, dedup_key)
);
CREATE INDEX idx_notif_unread ON notifications (user_id, read_at);

CREATE TABLE audit_logs (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID REFERENCES users(id),
  action      audit_action NOT NULL,
  entity      TEXT,
  entity_id   UUID,
  payload     JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user ON audit_logs (user_id, created_at);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at);

```

## 1. 제품이 푸는 문제

6개월 뒤 유로 3,000이 필요하지만 환율은 매일 변한다. **방향 예측은 포기하고(Meese–Rogoff, 1983) 분산을 줄인다.**

- 일시불 vs 6회 분할 → **평균 매수 단가 기댓값은 동일**, 표준편차는 분할이 ~65% 수준.
- 즉 "돈 벌어줄게"가 아니라 "**최악을 줄여줄게**"가 제품 가치.

## 2. 예측의 세 층위 ⭐ (가장 헷갈리는 부분)

"환율을 예측하지 않는다"는 부정확하다. 정확히는 **예측 가능한 것만** 예측한다.

| 층위 | 예측? | 계산 입력? | 근거 |
|---|---|---|---|
| **L1 변동폭** (얼마나 흔들릴까) | ✅ 한다 | ✅ 분할 횟수·달성 확률·트리거 입력 | 변동성 군집. 자기상관이 강해 실제 예측됨(GARCH) |
| **L2 방향** (오를까 내릴까) | ⚠️ 한다(참고 경로·동인) | ❌ **어디에도 안 넣음** | Meese–Rogoff. 성적표를 공개하고 판단은 사용자에게 |
| **L3 방향 기반 지시** (지금 사라/팔라) | ❌ 안 함 | — | 못 맞히는 걸 근거로 행동 지시는 도박 |

**스키마가 이를 강제**한다:
- `forecasts.base_rate` — L1, 계산이 읽는 유일한 중앙값(= 기준일 환율).
- `forecasts.model_path` (JSONB) — L2, 방향 전망, **화면 표시 전용**.
- `forecast_factors.*` — L2, 동인 화살표, 화면 표시 전용.

플래너 쿼리에 `model_path`가 등장하면 코드 리뷰에서 바로 잡힌다. L1·L2가 전부 실패해도 플래너는 균등 분할로 폴백하며 제품은 살아남는다.

## 3. 핵심 개념

- **삼각환산**: `JPYKRW`·`EURKRW`는 저장하지 않고 매번 유도. `JPYKRW = USDKRW / USDJPY`, `EURKRW = USDKRW × EURUSD`. 셋 다 저장하면 캐시 정합성이 깨진다. 방향은 `currencies.usd_side`(Postgres ENUM `usd_pair_side`: `self`/`base`/`quote`/`none`)로 결정.
- **100엔 표기**: DB엔 1엔 기준 저장, 표시 때만 `quote_unit`(JPY만 100, CHECK로 1 또는 100만 허용) 곱함.
- **반올림 자릿수**: `currencies.minor_units`(USD 2, JPY 0). `CHECK (minor_units BETWEEN 0 AND 4)`로 범위를 강제한다.
- **스프레드 = 마크업**: `list_spread=0.01`이면 살 때 `rate × 1.01`.
- **우대율 = 할인 쿠폰**: `실효스프레드 = list_spread × (1 - fx_discount_ratio)`.
- **전신환(`tt`) vs 현찰(`cash`)**: 현찰이 더 비쌈(실물 보관·운송).
- **변동성 σ**: 표준편차. 시간에 대해 제곱근으로 커짐 → `σ_T = σ_30 × √(T/30)`.
- **안전/기회 버킷** ⭐: 안전은 정해진 날 무조건 실행(`setInterval`), 기회는 목표 환율 도달 시 실행·미도달 시 마감일 강제(`Promise.race`).
- **집중도·상관계수 ρ**: 분산 효과 공식 `σ_p² = ΣΣ wᵢwⱼρᵢⱼσᵢσⱼ`에 ρ 필요.

### 목적별 안전 버킷 하한 (참고 — 산출 로직은 v3 열린 항목)

`goals.purpose`별 안전자산 비중 프로파일의 **후보값**이다. v3 §14는 `plans`의 목적함수·안전/기회 버킷 구성·`safe_ratio` 산출 로직을 "Route 상세설계 확정 후"로 남겨두었으므로, 아래 수치는 확정이 아니라 참고다.

| purpose | 기본(후보) | 하한(후보) | 이유 |
|---|---|---|---|
| `invest` 해외주식 적립 | 50% | 35% | 매수를 며칠 미뤄도 재앙 아님 |
| `once` 일회성 매수 | 70% | 50% | 날짜가 정해져 있음 |
| `travel` 여행 | 85% | 70% | 출발일은 못 미룸 |
| `tuition` 학비·송금 | 95% | 90% | 기한 놓치면 대체 수단 없음 |

확정되면 하한은 성향(`risk_profiles.safe_ratio_adjust`)보다 우선하는 불변식으로, 프론트 슬라이더 min·서비스 검증·DB 제약(`plans.ck_safe_ratio`) 3중으로 막는 것을 목표로 한다.

---

## 4. 도메인별 테이블 (32개)

### A. 마스터·기준정보 (7)
세상의 상수. 통화 동작 규칙·수수료·시나리오를 코드가 아닌 데이터로 보관.

- **`currencies`** ⭐ — 통화 마스터. `currency_code`(PK, 모든 FK가 동일 이름), `name_ko`·`symbol`·`minor_units`·`quote_unit`·`usd_side`(ENUM)·`is_home_currency`(KRW만 true, 부분 유니크 인덱스로 1개만)·`is_supported`·`support_note`·`color_token`·`sort_order`. `CHECK`로 `minor_units∈[0,4]`·`quote_unit∈{1,100}`. 통화 추가 = INSERT 1행 + CSS 토큰 1줄.
- **`currency_pairs`** ⭐ — 통화쌍 마스터. `pair_code`(PK), `base/quote_currency_code`, `is_stored`(저장 쌍은 USDKRW·USDJPY·EURUSD 3개뿐), `derive_via_pair_code`. `CHECK`로 저장 쌍은 유도경로 NULL, 유도 쌍은 경로 필수. FK 대상을 만들어 오타를 DB가 막게 함.
- **`banks`** — 은행 마스터. `bank_code`(PK)·`name_ko`.
- **`bank_fx_terms`** ⭐ — 환전 조건. (은행 × 통화 × 채널) 3차원. `channel`(ENUM `tt`/`cash`)·`list_spread`·`fixed_fee_krw`(기본 0)·`min_amount`·`effective_from`. `(bank_code, currency_code, channel, effective_from)` 유니크.
- **`securities`** ⭐ — 종목 마스터. `ticker`(PK)·`currency_code`(거래 통화)·`exchange`·`asset_type`(ENUM `stock`/`etf`/`adr`/`reit`)·`is_supported`. 사용자에게 통화를 묻지 않는 근거.
- **`factors`** ⭐ — 전망 동인 마스터(L2). `factor_code`(PK)·`name_ko`·`category`(ENUM `rate_diff`/`risk_sentiment`/`flow`/`policy`/`technical`)·`source_series`.
- **`stress_scenarios`** ⭐신규 — 스트레스 시나리오 마스터. `scenario_code`(PK, 예 `equity_down_krw_weak`)·`name_ko`·`equity_shock_pct`·`fx_shock_pct`(가정 충격률)·`reference_event`(참고 실제 사건)·`assumption_note`(근거 보기 노출)·`is_default`(기본 2종)·`sort_order`.

### B. 사용자 (4)

- **`users`** — `id`·`email`·`password_hash`(bcrypt)·`name`·`onboarded_at`(NULL이면 온보딩 리다이렉트)·`is_demo`(집계에서 제외)·`created_at`·`deleted_at`(소프트 삭제).
- **`user_settings`** (1:1) — `user_id`(PK=FK)·`default_bank_code`·`fx_discount_ratio`·**`explain_level`**(ENUM `simple`/`standard`/`detailed` = 설명 선호 3단계 "핵심만 쉽게/숫자와 이유/지표와 한계")·**`explain_domain`**(ENUM `finance`/`dev`/`marketing`/`plain`, 기본 `plain`)·알림 5종 플래그(`notify_step_due`/`regime_shift`/`deadline_near`/`target_zone`/`concentration`). ⚠️ `explain_level`·`explain_domain`은 **절대 계산에 넣지 않음**(문구·비유·설명 밀도만).
- **`user_banks`** ⭐ (N:M) — `user_id`+`bank_code`(복합 PK)·`is_primary`·`memo`. 여행은 A은행 현찰, 해외주식은 B은행 전신환처럼 목적별로 나눠 씀.
- **`risk_profiles`** — 위험성향 이력 테이블(간편 재응답은 새 row). 주요 컬럼:
  - **`grade`** (ENUM 4종) — `stable`(안정항로형)/`balanced`(균형항로형)/`aggressive`(적극항로형)/`challenging`(도전항로형).
  - **`score`** `SMALLINT` — 간편 진단 Q1~Q3 **합계 0~9**(`CHECK 0~9`). 구간: **0-2 stable / 3-4 balanced / 5-6 aggressive / 7-9 challenging**.
  - `concentration_threshold`·`safe_ratio_adjust` — grade별 참고 기준선(집중도 경고선 + 안전 버킷 가감).
  - `answers`(JSONB) — 간편 Q1~Q3 응답 원본(유형 판정 근거).
  - **`detail_answers`**(JSONB, nullable) — 상세 Q4~Q6(자금 분리·설명 선호·보유 경험) 완료 응답. **`grade`·`score`에 영향 없음.**
  - **`detail_progress`**(JSONB, nullable) — 상세 진단 중단 시점 응답(재개용). 완료되면 `detail_answers`로 옮기고 비움.
  - **`status`**(ENUM) — `not_measured`/`simple_done`/`detail_done`. 마이페이지에 그대로 노출.
  - `is_manual`·`created_at`·`updated_at` — 최근 row(created_at)가 현재 대표 성향. 상세 진단은 같은 row를 갱신(updated_at)하며, 간편 진단의 grade/score를 바꾸지 않는다.

### C. 자산 (4)
외화 자산이 세 종류. `총자산 = Σ krw_assets + Σ(fx_deposits × rate) + Σ(holdings × price × rate)`.

- **`holdings`** — 해외 주식·ETF. `ticker`(FK, 통화 따라옴)·`quantity`·`avg_cost`(거래통화 기준)·`purchased_on`(사용자 입력 유일 시점)·`purchase_fx_rate`(서버 자동 조회)·`fx_rate_entry_method`(auto/manual).
- **`fx_deposits`** — 외화 예금. `currency_code`·`bank_code`·`balance`·`avg_acquired_rate`(가중평균 취득 환율, 환전마다 재계산). `(user_id, currency_code, bank_code)` 유니크.
- **`krw_assets`** ⭐ — 원화 자산. `kind`(cash/deposit/domestic_equity/other)·`label`·`amount_krw`(소수점 없음). 외화 비중의 분모.
- **`portfolio_snapshots`** — 일별 스냅샷. `snapshot_date`·`total_krw`·`fx_krw`·`krw_only`·`exposure`(JSONB). 매일 자정 배치가 채움("어제 대비" 표시용).

### D. 목표·계획 (4) — 핵심 도메인
`goals(뭐가 필요) → plans(어떻게 살까, 버전으로 쌓임) → plan_steps(회차) → executions(실제 매수)`. `plans`는 git commit 같이 UPDATE 아닌 새 버전 INSERT.

- **`goals`** — `kind`(ENUM recurring→`min Var(평균 진입가)` / deadline→`min P(예산 초과)`)·`purpose`(ENUM invest/once/travel/tuition, 안전 버킷 프로파일 키)·`currency_code`·`target_amount`(외화)·`target_date`·`recur_interval`(ENUM weekly/monthly/quarterly)·`budget_amount`(원값 그대로)·`budget_currency_code`(기본 KRW)·`budget_period`(ENUM monthly/total)·`is_speculative`·`status`(ENUM active/paused/completed/cancelled)·`priority`.
- **`plans`** — 컬럼 3그룹: ① 식별·이력(`version`·`reason`(ENUM initial/skipped/safe_mode/user_edit/replan/rollover)·`reason_detail`·`is_active`(goal당 최대 1개, 부분 유니크 인덱스)) ② **스냅샷**(`held/rate/sigma/spread/fixed_fee_snapshot`·`risk_profile_id` — 불변, 과거 재현용) ③ 파라미터·산출(`safe_ratio`·`split_count`·`weeks`·`opportunity_amount`(단일 대기 물량)·`opportunity_trigger_rate`·`achieve_prob`·`entry_sigma`·`worst5_rate`·`total_fee_krw`). 계산 로직 확정 전이라 산출 컬럼은 nullable, `safe_ratio`는 `CHECK 0~1`.
- **`plan_steps`** — `seq`·`scheduled_date`·`amount`·`executed_amount`(CHECK ≤ amount)·`is_final_safe_date`·`status`(ENUM pending/done/partial/skipped/forced — `partial`=일부 집행, `forced`=최종안전일 강제). **모든 step은 안전 버킷**(기회 버킷은 `plans.opportunity_amount` 단일 물량).
- **`executions`** — 실제 매수. `step_id`(계획 밖이면 NULL)·`currency_code`·`bank_code`·`channel`(ENUM tt/cash)·`executed_at`·`amount`·`applied_rate`(실제 체결가, `fx_rates` 종가와 다름)·`fee_krw`·`krw_paid`·`entry_method`(ENUM manual/csv_import/open_banking). ⚠️ 전부 수동 입력이라 리마인더가 데이터 품질의 유일한 방어선.

### E. 시장 데이터 (10)
사용자와 무관한 공용 데이터. 배치가 채움.

- **`fx_rates`** — 일별 환율. `pair_code`+`quote_date`+`rate_type`(mid/tt_buy/tt_sell/cash_buy/cash_sell) 복합 PK. `rate`(1 외화당 원화)·`data_source`·`fetched_at`. 통계·전망은 `mid`, 비용은 `tt_sell`·`cash_sell`.
- **`fx_stats`** — 변동성 통계. `vol_7d`·`vol_30d`(σ 스케일링 기준)·`vol_90d`·`vol_percentile_5y`·`regime`(calm/normal/elevated/stress).
- **`fx_correlations`** ⭐ — 상관계수. `pair_code_a < pair_code_b` 정규화·`window_days`·`as_of`·`rho`(−1~+1).
- **`security_prices`** ⭐ — 종목 종가. `ticker`+`price_date`·`close_price`·`data_source`(ENUM `price_source`: yahoo/alphavantage/manual). ⚠️ 소스 확정은 v3 열린 항목(§7 아래 열린 항목 참고).
- **`forecasts`** — 전망. `base_rate`(**L1**, 계산이 읽는 유일 중앙값)·`model_path`(**L2**, 화면 표시 전용)·`p50/p80_lo/hi`(L1, 80% 하단은 기회 버킷 트리거)·`model_version`.
- **`forecast_factors`** — 전망 동인(L2·참고). `direction`(±1)·`strength`(0~1)·`latest_value`. 화면엔 "참고" 배지+모델 적중률 병기.
- **`model_runs`** — 모델 성적표. `hit_rate`(L2 성적, ~51%면 그대로 표시)·`mae`·`coverage_80`(L1 성적)·`avg_width`(coverage와 반드시 함께)·`rw_improvement`(음수여도 표시).
- **`econ_events` + `econ_event_pairs`** — 경제 일정. `event_date`·`region`·`title`·`impact`(1~3), 그리고 이벤트↔통화쌍 매핑(USD 화면에 일본 지표가 뜨는 걸 방지).
- **`macro_series`** — 거시지표 원계열. `series_code`+`obs_date`·`value`·`data_source`(FRED/ECOS). `factors`가 논리적으로 참조.

### F. Stress Test (1) — 신규 도메인

- **`stress_test_runs`** ⭐신규 — 스트레스 테스트 실행 결과. `user_id`·`scenario_code`(→`stress_scenarios`)·`base_date`·**`equity_shock_pct`·`fx_shock_pct`**(실행 시점 충격률 스냅샷 — 이후 마스터가 바뀌어도 과거 실행 재현)·**`equity_effect_krw`·`fx_effect_krw`·`total_effect_krw`**(주가/환율/총 평가금액 효과 분리)·`snapshot_date`(→`portfolio_snapshots` 기준 포트폴리오, 선택). 삭제하지 않는다 — 사용자에게 노출된 계산 근거. 결과 화면은 "예측이 아니라 가정한 충격의 계산"임을 이 값들과 함께 보여준다.

### G. 지원 (2)

- **`notifications`** — `type`(ENUM step_due/regime_shift/deadline_near/target_zone/safe_mode/concentration)·`dedup_key`(`(user_id, dedup_key)` 유니크로 중복 발송 방지)·`title`·`body`·`sent_at`·`read_at`.
- **`audit_logs`** — `action`(ENUM plan_created/plan_recalculated/ai_explained/**warning_shown**/forecast_served/data_stale)·`entity`·`entity_id`·`payload`(JSONB, AI 프롬프트·응답·사용자에게 표시된 경고 전문). `warning_shown`은 사후 검증·분쟁 대응에 필수.

---

## 5. 컬럼 이름 규칙 (v2.1 네이밍)

| 패턴 | 의미 | 예 |
|---|---|---|
| `_krw` | 원화 금액 `NUMERIC(18,0)` 소수점 없음 | `krw_paid`, `total_fee_krw` |
| `amount` (접미사 없음) | 외화 금액 `NUMERIC(18,4)` | `target_amount` |
| `_rate` | **환율에만** `NUMERIC(14,6)` 1 외화당 원화 | `applied_rate`, `base_rate` |
| `_ratio`·`_pct`·`vol_`·`rho`·`_prob` | **비율에만**. 예외 `hit_rate` | `safe_ratio`, `fx_shock_pct`, `achieve_prob` |
| `_code` | 도메인 식별자, PK·FK 동일 이름(`USING` 조인) | `currency_code`, `pair_code` |
| `data_source` | 어디서 긁어왔나(외부 공급자) | `fx_rates.data_source`=ECOS |
| `entry_method` | 누가 입력했나(사람/자동) | `executions.entry_method`=manual |
| `list_` | 할인 적용 전 고시값 | `list_spread` |
| `_snapshot` | 불변, 계산 시점 값 박음 | `rate_snapshot` |
| `_at`/`_on`·`_date` | TIMESTAMPTZ / DATE | `executed_at` / `purchased_on` |
| `is_` | BOOLEAN | `is_active`, `is_demo` |
| `fx_` 접두사 | 사용자와 무관한 공용 시장 데이터 | `fx_rates`, `fx_stats` |

## 6. 구현 순서 제안

| 단계 | 테이블 | 완성 |
|---|---|---|
| 1 | currencies·currency_pairs·banks·bank_fx_terms·securities·stress_scenarios | 시드 데이터 |
| 2 | fx_rates·fx_stats + ECOS 수집 배치 | 환율 조회·삼각환산 |
| 3 | users·user_settings·risk_profiles | 로그인·온보딩 |
| 4 | krw_assets·fx_deposits·holdings·security_prices | **홈 + 내 자산** |
| 5 | goals·plans·plan_steps | **환전 플래너** (핵심) |
| 6 | executions | 실행 기록·진행률 |
| 7 | forecasts·forecast_factors·factors·model_runs | 환율 범위 화면 |
| 8 | fx_correlations·portfolio_snapshots·stress_test_runs·notifications·audit_logs | 분산 효과·추이·스트레스·알림·감사 |

**4·5단계만 있으면 데모가 된다.** 7단계(전망)는 없어도 제품 성립(방향을 팔지 않으므로).

## 7. 자주 하는 오해
- "환율을 예측해 싼 타이밍을 알려준다" → 절반만 맞다. 변동폭(L1) 예측, 방향(L2) 참고만.
- "분할 매수하면 평균적으로 이득" → 아니다. 기댓값은 같고 **최악만** 좋아진다.
- "공격적 성향이면 안전 버킷 0까지 가능" → 아니다. `purpose` 하한이 성향보다 우선.
- "계획 바뀌면 UPDATE" → 아니다. 새 `version` INSERT. 이력이 곧 제품 가치.
- "`coverage_80`이 82%면 좋은 모델" → 모른다. `avg_width`를 같이 봐야 함.
