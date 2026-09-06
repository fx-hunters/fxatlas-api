# API 명세서 v1.0

> 원본: Notion「API 명세서」(FX ATLAS API v1.0)
> Base URL: `https://api.fx-atlas.app/v1` · 모든 응답 `application/json; charset=utf-8`
> **필드명은 DB 컬럼명과 동일**하다: `currency_code`, `pair_code`, `entry_method` 등.

## 1. 공통 규약

### 1.1 인증
```
Authorization: Bearer {access_token}
```
- 액세스 토큰 30분, 리프레시 토큰 14일.
- 데모 계정은 `POST /auth/demo`로 발급, 토큰에 `is_demo: true` 포함.

### 1.2 응답 형식
성공 응답은 단일 루트 객체. **계산 수치가 포함되면 `meta` 필수.**
```json
{
  "data": { },
  "meta": {
    "as_of": "2026-09-01T15:30:00Z",
    "sources": ["ECOS", "FRED"],
    "model_version": "fc-2026.08.3",
    "is_demo": true
  }
}
```
`meta.as_of`와 `meta.sources`는 수치 반환 엔드포인트에서 필수(NFR-DT-01). 출처 없는 숫자를 내려보내지 않는다.

### 1.3 에러 형식
```json
{
  "error": {
    "code": "SAFE_RATIO_BELOW_FLOOR",
    "message": "학비 목표의 안전 버킷은 90퍼센트 아래로 내릴 수 없습니다.",
    "field": "safe_ratio",
    "detail": { "floor": 0.90, "requested": 0.40 }
  }
}
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | VALIDATION_FAILED | 입력값 형식 오류 |
| 400 | SAFE_RATIO_BELOW_FLOOR | 안전 버킷 하한 위반 |
| 400 | DEADLINE_TOO_SOON | 마감일 2주 미만, 분할 무의미 |
| 401 | UNAUTHORIZED | 토큰 없음/만료 |
| 403 | SPECULATIVE_PURPOSE_BLOCKED | 투기 목적 게이트, 개인화 출력 차단 |
| 404 | NOT_FOUND | — |
| 409 | ACTIVE_PLAN_EXISTS | 활성 계획 이미 존재 |
| 422 | BUDGET_INSUFFICIENT | 예산으로 계획 불성립, 대안을 detail에 |
| 503 | SAFE_MODE_ACTIVE | 안전모드로 전망 산출 중단 |

### 1.4 표기 규약
- 날짜: ISO 8601 (날짜 `2026-10-15`, 시각은 UTC `Z`).
- 통화: `currency_code` ISO 4217 3자리 대문자. 통화쌍: `pair_code` `USDKRW` 6자리.
- 금액: 숫자형(문자열 금지). 외화 소수 4자리, 원화 정수.
- 비율: 0~1 소수(`0.85`=85%).
- JPY: 저장·전송은 1엔 기준, 100엔 환산은 클라이언트 표시 단계.
- 부호: 환율 상승=원화 약세=외화자산 이익(+).

## 2. 엔드포인트 전체 목록

**인증**
| METHOD | PATH | 설명 |
|---|---|---|
| POST | /auth/signup | 회원가입 |
| POST | /auth/login | 로그인 |
| POST | /auth/refresh | 토큰 갱신 |
| POST | /auth/demo | 샘플 계정 발급 |

**마이페이지**
| METHOD | PATH | 설명 |
|---|---|---|
| GET/PUT | /me | 프로필 조회/수정 |
| GET/PUT | /me/risk-profile | 투자성향 조회/재진단 |
| GET/PUT | /me/settings | `default_bank_code`·`fx_discount_ratio`·`display_mode` |
| PUT | /me/notifications | 알림 설정 |

**자산**
| METHOD | PATH | 설명 |
|---|---|---|
| GET/POST | /holdings | 보유 종목 목록/추가 |
| PUT/DELETE | /holdings/{id} | 종목 수정/삭제 |
| GET/POST | /deposits | 외화 예금 목록/추가 |

**진단**
| METHOD | PATH | 설명 |
|---|---|---|
| GET | /xray | 통화 노출·외화 비중·민감도 |
| GET | /xray/attribution | 손익 분해 |
| POST | /xray/stress | 스트레스 시나리오 적용 |
| GET | /fit/concentration | 집중도 진단 |
| POST | /fit/simulate | 분산효과 시뮬레이션 |

**환율 범위**
| METHOD | PATH | 설명 |
|---|---|---|
| GET | /forecast | 팬차트·구간·변동성 |
| GET | /forecast/factors | 전망 동인 |
| GET | /forecast/model-performance | 모델 성적표 |
| GET | /events | 경제 일정 |

**목표·계획**
| METHOD | PATH | 설명 |
|---|---|---|
| GET/POST | /goals | 목표 목록/생성 |
| GET/PUT/DELETE | /goals/{id} | 목표 상세/수정/삭제 |
| POST | /plans/preview | **계획 미리보기. 저장 안 함** |
| POST | /goals/{id}/plans | 계획 확정·저장 |
| GET | /goals/{id}/plans | 계획 버전 이력 |
| GET | /goals/{id}/plans/active | 활성 계획·회차 |
| POST | /plans/{id}/steps/{seq}/complete | 회차 완료 기록 |
| POST | /plans/{id}/steps/{seq}/skip | 회차 건너뛰기 |

**마스터·기타**
| METHOD | PATH | 설명 |
|---|---|---|
| GET | /currencies | 지원 통화·표시 규칙 (`minor_units`·`quote_unit`·`usd_side`·`color_token`) |
| GET | /banks/{bank_code}/fx-terms | 은행·통화·채널별 `list_spread`·`fixed_fee_krw` |
| GET | /home/summary | 홈 3블록 통합 조회 |
| GET | /system/safe-mode | 안전모드 상태 |
| GET | /notifications | 알림 목록 |

## 3. 핵심 엔드포인트 상세

### 3.1 POST /plans/preview — 계획 엔진 (제품의 심장)
슬라이더를 움직일 때마다 호출되며 **저장하지 않는다.**

**Request** — `safe_ratio`·`split_count` 생략 시 서버가 목적·성향·변동성으로 권장값 산출.
```json
{ "goal_id": "9f2c...", "weekly_budget_krw": 426923, "safe_ratio": 0.50, "split_count": 6 }
```

**Response 200** (주요 필드)
```json
{
  "data": {
    "goal": { "kind": "recurring", "purpose": "invest", "currency_code": "USD" },
    "unfunded": 1740.0, "weeks": 6, "sigma_horizon": 0.0730,
    "buckets": { "safe": 870.0, "opportunity": 870.0, "safe_ratio": 0.50, "floor": 0.35 },
    "split": { "count": 6, "interval_days": 7, "g_factor": 0.6491,
               "next_step_delta": { "sigma_gain": 0.0026, "fee_increase_krw": 1000 } },
    "steps": [ { "seq": 1, "scheduled_date": "2026-09-02", "amount": 145.0,
                 "krw_estimate": 201049, "executed_amount": 0, "status": "pending" } ],
    "opportunity": { "amount": 870.0, "trigger_rate": 1346.0,
                     "final_safe_date": "2026-10-08",
                     "note": "미실행 시 최종 안전 환전일에 안전 버킷으로 편입" },
    "metrics": { "hero": "entry_sigma", "entry_sigma": 0.0474, "entry_sigma_once": 0.0730,
                 "achieve_prob": 0.85, "worst5_rate": 1522.0,
                 "fee": { "spread_krw": 7216, "fixed_krw": 6000, "total_krw": 13216 } },
    "comparison": [ /* lump_at_deadline · even_split · current_plan */ ],
    "concentration": { "before": {...}, "after": {...}, "threshold": 0.60, "verdict": "worsens" },
    "warnings": [ { "code": "HIGH_VOLATILITY", "message": "USD 변동성이 상위 28퍼센트 수준입니다." } ]
  },
  "meta": { "as_of": "...", "sources": ["ECOS"],
            "simulation": { "method": "monte_carlo", "iterations": 4000, "antithetic": true } }
}
```

주요 필드 의미:
- `metrics.hero`: `entry_sigma`(recurring) 또는 `achieve_prob`(deadline). 클라이언트가 큰 숫자로 표시할 값 결정.
- `split.g_factor`: 분산 감소 계수 g(N), 검증·디버깅용.
- `split.next_step_delta`: 분할 1회 증가 시 이득/비용, 슬라이더 힌트용.
- `buckets.floor`: 목적별 안전 버킷 하한, 슬라이더 min.
- `steps`: 전부 안전 버킷. 회차에 `bucket` 필드 없음.
- `opportunity`: 회차가 아닌 단일 대기 물량 (`plans.opportunity_amount`·`opportunity_trigger_rate`에 저장).
- `comparison`: 세 전략의 `avg_rate`는 거의 같아야 정상(다르면 대조표본법 의심).
- `concentration.verdict`: `worsens`/`improves`/`neutral`.

**Response 422 — 예산 부족**: `achieve_prob`과 대안(`increase_budget`/`extend_deadline`/`reduce_target`)을 detail에 담는다.

### 3.2 POST /goals — 목표 생성
```json
{ "name": "미국 ETF 적립", "kind": "recurring", "purpose": "invest",
  "currency_code": "USD", "target_amount": 3000.0, "target_date": "2026-10-15",
  "recur_interval": "monthly", "budget_amount": 1850000,
  "budget_currency_code": "KRW", "budget_period": "monthly", "is_speculative": false }
```
- `kind=recurring`이면 `purpose`는 `invest`만 허용.
- `held_amount`는 받지 않음 — 서버가 `/deposits`에서 조회.
- `is_speculative:true`면 201 생성되되 이후 `/plans/preview`가 403 반환.
- **201 응답**은 `held_amount`와 `suggested`(권장 `safe_ratio`·`floor`·`split_count`) 포함.

### 3.3 GET /xray
총자산·외화자산·외화 비중(`fx_ratio`), 통화별 `exposure`(krw·share), `concentration`(top_currency·share·threshold·status), `sensitivity_1pct`(합계·통화별), `day_change_krw`, `upcoming_outflows`(예정 지출) 반환.

### 3.4 GET /xray/attribution
쿼리 `?currency_code=USD&mode=three_way`

| mode | 의미 |
|---|---|
| three_way | 자산/환율/교차항 분리 (전문가 모드 기본) |
| shapley | 교차항을 자산·환율에 절반씩 배분 (초보자 모드 기본) |

`components`(asset·fx·interaction·cost의 krw·기여 pp)와 `by_holding` 반환.

### 3.5 GET /forecast
쿼리 `?pair_code=USDKRW&horizon=30`. `current_rate`·`base_rate`·`history`·`path`(p50/p80 구간)·`model_path`(표시 전용)·`interval_80`·`volatility`(realized·percentile·regime)·`user_impact`·`disclaimer` 반환.

- **방향 확률 필드를 두지 않는다.**
- `base_rate`는 드리프트 0 기준선이며 계산에 쓰이는 유일한 중앙값. `model_path`는 표시 전용으로 `/plans/preview`에 전달되지 않음.
- JPYKRW·EURKRW 요청 시 USDKRW·USDJPY·EURUSD에서 유도해 삼각 무차익 조건 충족.

### 3.6 GET /forecast/model-performance
`model`(hit_rate·mae·coverage_80·avg_width)·`random_walk`·`validation`(rolling_walk_forward·folds·leakage_guard) 반환. 구간 포함률은 폭과 함께 봐야 한다.

### 3.7 POST /plans/{id}/steps/{seq}/skip
`redistributed`(회차당 금액 재분배)·`achieve_prob`(before/after)·`consecutive_skips`·`safe_mode_triggered`·`new_plan_version` 반환. 연속 건너뛰기가 임계치 도달 시 `safe_mode_triggered:true`, 새 계획은 `reason:"safe_mode"`.

### 3.8 POST /fit/simulate
```json
{ "currency_code": "JPY", "delta_share": 0.10 }
```
`portfolio_vol`(before/after)·`exposure_after`·`threshold`·`within_threshold`·`suggested_goal` 반환. `suggested_goal`이 Fit→플래너 다리 역할(클라이언트가 목표 생성 폼을 채움).

### 3.9 GET /system/safe-mode
`active`·`status`(`normal`/`caution`/`safe_mode`)·`checks`(data_freshness·source_divergence·interval_breach_95·vol_percentile_95·deadline_underfunded·consecutive_skips) 반환. `status`는 홈 상태 라벨에 그대로 사용.

## 4. AI 엔드포인트 (확장)

| METHOD | PATH | 설명 |
|---|---|---|
| POST | /ai/parse-goal | 자연어 문장 → 목표 제약 구조화 |
| POST | /ai/explain | 엔진 결과를 설명 프로필에 맞춰 서술 |

```json
// POST /ai/parse-goal  { "text": "매달 700달러씩 미국 ETF를 사려고 해요" }
// Response
{ "data": { "parsed": { "kind": "recurring", "purpose": "invest",
                        "currency_code": "USD", "target_amount": 700.0, "recur_interval": "monthly" },
            "confidence": { "currency_code": 0.98, "target_amount": 0.95, "target_date": 0.20 },
            "missing": ["target_date", "budget_amount"] } }
```

**AI 경계 (NFR-AI-01~03)**:
1. `/ai/*`는 구조화·서술만 한다. 금액·확률·비용을 계산하지 않는다.
2. `/ai/explain`은 입력받은 엔진 수치를 그대로 인용해야 하며, 검증기가 숫자 불일치를 감지하면 폐기.
3. `confidence` 낮은 필드는 클라이언트에서 강조해 사용자 확인. 승인 전 저장 안 함.

## 5. 호출 순서 예시
```
GET /xray → POST /fit/simulate → POST /goals (201, suggested)
→ POST /plans/preview (엔진: g(N)·몬테카를로 4000회, 저장 안 함)
→ POST /goals/{id}/plans (201, version 1, is_active true)
```

## 6. 미결정 항목
- `/plans/preview`를 매 슬라이더 이벤트마다 호출할지, 클라이언트 계산 후 확정 시에만 서버 호출할지.
- 몬테카를로 시드를 서버가 고정할지(같은 입력=같은 확률).
- 알림 발송 방식(푸시 vs 인앱 인박스).
- 투기 목적 판별을 자기신고로만 할지 휴리스틱을 더할지.
