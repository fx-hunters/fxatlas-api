# ERD 간략 설명 (데이터 모델 v2.0)

> 원본: Notion「ERD (간략 설명 버전)」— 경제 배경 없이 구현하는 개발자 대상.
> ERD v2.0은 **6개 도메인 30개 테이블**. 시나리오: 사용자 `희찬`이 2027-03 유럽여행에 €3,000이 필요해 목표를 만들고 예산 500만원으로 6회 분할, 3회차까지 환전한 상태. 해외주식 `VOO` 12.5주 보유.

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

- **삼각환산**: `JPYKRW`·`EURKRW`는 저장하지 않고 매번 유도. `JPYKRW = USDKRW / USDJPY`, `EURKRW = USDKRW × EURUSD`. 셋 다 저장하면 캐시 정합성이 깨진다. 방향은 `currencies.usd_side`(`self`/`base`/`quote`/`none`)로 결정.
- **100엔 표기**: DB엔 1엔 기준 저장, 표시 때만 `quote_unit`(JPY만 100) 곱함.
- **반올림 자릿수**: `currencies.minor_units`(USD 2, JPY 0, KWD 3). ⚠️ 계산 규칙 명세서에 USD가 0자리로 잘못 적혀 있어 수정 필요.
- **스프레드 = 마크업**: `list_spread=0.01`이면 살 때 `rate × 1.01`.
- **우대율 = 할인 쿠폰**: `실효스프레드 = list_spread × (1 - fx_discount_ratio)`.
- **전신환(`tt`) vs 현찰(`cash`)**: 현찰이 더 비쌈(실물 보관·운송).
- **변동성 σ**: 표준편차. 시간에 대해 제곱근으로 커짐 → `σ_T = σ_30 × √(T/30)`.
- **안전/기회 버킷** ⭐: 안전은 정해진 날 무조건 실행(`setInterval`), 기회는 목표 환율 도달 시 실행·미도달 시 마감일 강제(`Promise.race`).
- **집중도·상관계수 ρ**: 분산 효과 공식 `σ_p² = ΣΣ wᵢwⱼρᵢⱼσᵢσⱼ`에 ρ 필요.

### 목적별 안전 버킷 하한 (불변식 — 성향보다 우선)

| purpose | 기본 | 하한 | 이유 |
|---|---|---|---|
| `invest` 해외주식 적립 | 50% | 35% | 매수를 며칠 미뤄도 재앙 아님 |
| `once` 일회성 매수 | 70% | 50% | 날짜가 정해져 있음 |
| `travel` 여행 | 85% | 70% | 출발일은 못 미룸 |
| `tuition` 학비·송금 | 95% | 90% | 기한 놓치면 대체 수단 없음 |

프론트 슬라이더 min, 서비스 계층 검증, DB 제약 — 3중으로 막는다.

---

## 4. 도메인별 테이블 (30개)

### A. 마스터·기준정보 (6)
세상의 상수. 통화 동작 규칙·수수료를 코드가 아닌 데이터로 보관.

- **`currencies`** ⭐ — 통화 마스터. `currency_code`(PK, 모든 FK가 동일 이름), `name_ko`·`symbol`·`minor_units`·`quote_unit`·`usd_side`·`is_home_currency`(KRW만 true)·`is_supported`·`support_note`·`color_token`·`sort_order`. 통화 추가 = INSERT 1행 + CSS 토큰 1줄.
- **`currency_pairs`** ⭐ — 통화쌍 마스터. `pair_code`(PK), `base/quote_currency_code`, `is_stored`(저장 쌍은 3개뿐), `derive_via_pair_code`. FK 대상을 만들어 오타를 DB가 막게 함.
- **`banks`** — 은행 마스터. `bank_code`(PK)·`name_ko`.
- **`bank_fx_terms`** ⭐ — 환전 조건. (은행 × 통화 × 채널) 3차원. `list_spread`·`fixed_fee_krw`·`min_amount`·`effective_from`. ⚠️ `fixed_fee_krw=0`이면 분할 N을 억제하는 힘이 사라져 `min_amount` 등 다른 상한 근거 필요.
- **`securities`** ⭐ — 종목 마스터. `ticker`(PK)·`currency_code`(거래 통화)·`exchange`·`asset_type`. 사용자에게 통화를 묻지 않는 근거.
- **`factors`** ⭐ — 전망 동인 마스터(L2). `factor_code`(PK)·`name_ko`·`category`·`source_series`.

### B. 사용자 (4)

- **`users`** — `id`·`email`·`password_hash`(bcrypt)·`name`·`onboarded_at`(NULL이면 온보딩 리다이렉트)·`is_demo`(집계에서 제외)·`created_at`·`deleted_at`(소프트 삭제).
- **`user_settings`** (1:1) — `user_id`(PK=FK)·`default_bank_code`·`fx_discount_ratio`·`display_mode`(beginner/expert)·`explain_domain`(finance/dev/marketing/plain)·알림 5종 플래그. ⚠️ `display_mode`·`explain_domain`은 **절대 계산에 넣지 않음**(표현만).
- **`user_banks`** ⭐ (N:M) — `user_id`+`bank_code`(복합 PK)·`is_primary`·`memo`.
- **`risk_profiles`** — 이력 테이블(UPDATE 아닌 INSERT). `grade`(stable/balanced/flexible)·`concentration_threshold`·`safe_ratio_adjust`·`answers`(JSONB)·`is_manual`·`created_at`(최근 레코드가 현재 성향). 성향의 역할: 안전 버킷 가감(하한은 못 넘음) + 집중도 경고 기준선 변경.

### C. 자산 (4)
외화 자산이 세 종류. `총자산 = Σ krw_assets + Σ(fx_deposits × rate) + Σ(holdings × price × rate)`.

- **`holdings`** — 해외 주식·ETF. `ticker`(FK, 통화 따라옴)·`quantity`·`avg_cost`(거래통화 기준)·`purchased_on`(사용자 입력 유일 시점)·`purchase_fx_rate`(서버 자동 조회)·`fx_rate_entry_method`(auto/manual).
- **`fx_deposits`** — 외화 예금. `currency_code`·`bank_code`·`balance`·`avg_acquired_rate`(가중평균 취득 환율, 환전마다 재계산). `(user_id, currency_code, bank_code)` 유니크.
- **`krw_assets`** ⭐ — 원화 자산. `kind`(cash/deposit/domestic_equity/other)·`label`·`amount_krw`(소수점 없음). 외화 비중의 분모.
- **`portfolio_snapshots`** — 일별 스냅샷. `snapshot_date`·`total_krw`·`fx_krw`·`krw_only`·`exposure`(JSONB). 매일 자정 배치가 채움("어제 대비" 표시용).

### D. 목표·계획 (4) — 핵심 도메인
`goals(뭐가 필요) → plans(어떻게 살까, 버전으로 쌓임) → plan_steps(회차) → executions(실제 매수)`. `plans`는 git commit 같이 UPDATE 아닌 새 버전 INSERT.

- **`goals`** — `kind`(recurring→`min Var(평균 진입가)` / deadline→`min P(예산 초과)`)·`purpose`(안전 버킷 기본·하한 결정)·`currency_code`·`target_amount`(외화)·`target_date`·`recur_interval`·`budget_amount`(원값 그대로)·`budget_period`·`is_speculative`·`status`·`priority`. v1.0의 죽은 컬럼 `flexibility`·`recurring` 제거.
- **`plans`** — 컬럼 3그룹: ① 식별·이력(`version`·`reason`(initial/skipped/safe_mode/user_edit/replan/rollover)·`reason_detail`·`is_active`(goal당 최대 1개, 부분 유니크 인덱스)) ② **스냅샷**(`held/rate/sigma/spread/fixed_fee_snapshot` — 불변, 과거 재현용) ③ 파라미터·산출(`safe_ratio`·`split_count`·`opportunity_amount`(단일 대기 물량)·`opportunity_trigger_rate`·`achieve_prob`·`entry_sigma`·`worst5_rate`·`total_fee_krw`).
- **`plan_steps`** — `seq`·`scheduled_date`·`amount`·`executed_amount`(CHECK ≤ amount)·`is_final_safe_date`·`status`. v2.0에서 `bucket` 컬럼 제거 — **모든 step은 안전 버킷.**
- **`executions`** — 실제 매수. `step_id`(계획 밖이면 NULL)·`currency_code`·`bank_code`·`channel`·`executed_at`·`amount`·`applied_rate`(실제 체결가, `fx_rates` 종가와 다름)·`fee_krw`·`krw_paid`·`entry_method`(manual/csv_import/open_banking). ⚠️ 전부 수동 입력이라 리마인더가 데이터 품질의 유일한 방어선.

### E. 시장 데이터 (10)
사용자와 무관한 공용 데이터. 배치가 채움.

- **`fx_rates`** — 일별 환율. `pair_code`+`quote_date`+`rate_type`(mid/tt_buy/tt_sell/cash_buy/cash_sell) 복합 PK. `rate`(1 외화당 원화)·`data_source`·`fetched_at`. 통계·전망은 `mid`, 비용은 `tt_sell`·`cash_sell`.
- **`fx_stats`** — 변동성 통계. `vol_7d`·`vol_30d`(σ 스케일링 기준)·`vol_90d`·`vol_percentile_5y`·`regime`(calm/normal/elevated/stress).
- **`fx_correlations`** ⭐ — 상관계수. `pair_code_a < pair_code_b` 정규화·`window_days`·`as_of`·`rho`(−1~+1).
- **`security_prices`** ⭐ — 종목 종가. `ticker`+`price_date`·`close_price`·`data_source`(yahoo/alphavantage/manual). ⚠️ 데이터 소스 미정.
- **`forecasts`** — 전망. `base_rate`(**L1**, 계산이 읽는 유일 중앙값)·`model_path`(**L2**, 화면 표시 전용)·`p50/p80_lo/hi`(L1, 80% 하단은 기회 버킷 트리거)·`model_version`.
- **`forecast_factors`** — 전망 동인(L2·참고). `direction`(±1)·`strength`(0~1)·`latest_value`. 화면엔 "참고" 배지+모델 적중률 병기.
- **`model_runs`** — 모델 성적표. `hit_rate`(L2 성적, ~51%면 그대로 표시)·`mae`·`coverage_80`(L1 성적)·`avg_width`(coverage와 반드시 함께)·`rw_improvement`(음수여도 표시).
- **`econ_events` + `econ_event_pairs`** — 경제 일정. `event_date`·`region`·`title`·`impact`(1~3), 그리고 이벤트↔통화쌍 매핑(USD 화면에 일본 지표가 뜨는 걸 방지).
- **`macro_series`** — 거시지표 원계열. `series_code`+`obs_date`·`value`·`data_source`(FRED/ECOS). `factors`가 논리적으로 참조.

### F. 지원 (2)

- **`notifications`** — `type`(step_due/regime_shift/deadline_near/target_zone/safe_mode/concentration)·`dedup_key`(`(user_id, dedup_key)` 유니크로 중복 발송 방지)·`title`·`body`·`sent_at`·`read_at`.
- **`audit_logs`** — `action`(plan_created/plan_recalculated/ai_explained/**warning_shown**/forecast_served/data_stale)·`entity`·`entity_id`·`payload`(JSONB, AI 프롬프트·응답·사용자에게 표시된 경고 전문). `warning_shown`은 사후 검증·분쟁 대응에 필수.

---

## 5. 컬럼 이름 규칙 (v2.1 네이밍)

| 패턴 | 의미 | 예 |
|---|---|---|
| `_krw` | 원화 금액 `NUMERIC(18,0)` 소수점 없음 | `krw_paid`, `total_fee_krw` |
| `amount` (접미사 없음) | 외화 금액 `NUMERIC(18,4)` | `target_amount` |
| `_rate` | **환율에만** `NUMERIC(14,6)` 1 외화당 원화 | `applied_rate`, `base_rate` |
| `_ratio`·`vol_`·`rho`·`_prob` | **비율에만** `NUMERIC(5,4)`. 예외 `hit_rate` | `safe_ratio`, `achieve_prob` |
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
| 1 | currencies·currency_pairs·banks·bank_fx_terms·securities | 시드 데이터 |
| 2 | fx_rates·fx_stats + ECOS 수집 배치 | 환율 조회·삼각환산 |
| 3 | users·user_settings·risk_profiles | 로그인·온보딩 |
| 4 | krw_assets·fx_deposits·holdings·security_prices | **홈 + 내 자산** |
| 5 | goals·plans·plan_steps | **환전 플래너** (핵심) |
| 6 | executions | 실행 기록·진행률 |
| 7 | forecasts·forecast_factors·factors·model_runs | 환율 범위 화면 |
| 8 | fx_correlations·portfolio_snapshots·notifications·audit_logs | 분산 효과·추이·알림·감사 |

**4·5단계만 있으면 데모가 된다.** 7단계(전망)는 없어도 제품 성립(방향을 팔지 않으므로).

## 7. 자주 하는 오해
- "환율을 예측해 싼 타이밍을 알려준다" → 절반만 맞다. 변동폭(L1) 예측, 방향(L2) 참고만.
- "분할 매수하면 평균적으로 이득" → 아니다. 기댓값은 같고 **최악만** 좋아진다.
- "공격적 성향이면 안전 버킷 0까지 가능" → 아니다. `purpose` 하한이 성향보다 우선.
- "계획 바뀌면 UPDATE" → 아니다. 새 `version` INSERT. 이력이 곧 제품 가치.
- "`coverage_80`이 82%면 좋은 모델" → 모른다. `avg_width`를 같이 봐야 함.
