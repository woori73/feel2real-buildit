# BUILD IT Chart Production Expansion Home Handoff — 2026-09-19

> 학원 PC(Academy PC)에서 집 PC(Home PC)로 BUILD IT Chart 공개 확장 작업을 인계하기 위한 문서입니다.

---

## 1. GITHUB & BASE STATE

- **Base Main SHA:** `80ffbddef10ba88f5a60b33d303da4b4667a8f46` (PR #318 merged)
- **Handoff Branch:** `chart/handoff-20260919-home`
- **Commit SHA:** `2f65dfc2d1b71aa512e09ff7b4b1a4aa235aa8ea`
- **Live Publication:** Publication 34 (공개 수: 83명)
- **PR #318 Merged Endpoints:**
  - `POST /api/admin/chart/tools/candidates/{id}/reevaluate` (dryRun 지원)
  - `GET /api/admin/chart/tools/scores/stale`

---

## 2. SCORE NORMALIZATION & PUBLIC COUNT 83 STATE

- **83명 재계산 결과:** 65명 동일, 18명 점수 상승 (하락 없음)
  - 대표 상승: #45 박정민 (17 → 63), #41 김시훈 (17 → 43), #46 김주경 (17 → 39), #12 권수진 (17 → 31)
  - Score=17 최저점 분포: 43명 → 25명 감소
  - 평균 점수: 42.93 → 약 45.00 상승
- **현재 운영 DB 상태:** 재계산 검증은 완료되었으나, 아직 production snapshot / publication 생성이 이루어지지 않아 실 운영 DB/홈페이지는 83명 기존 snapshot 표시 중.

---

## 3. RESEARCH_PENDING CANDIDATES & CANDIDATE #100 STATUS

### 전체 RESEARCH_PENDING 목록 (26명)
IDs: `51, 52, 53, 55, 59, 62, 66, 77, 78, 79, 80, 82, 83, 84, 90, 91, 93, 94, 96, 99, 100, 101, 102, 106, 108, 109`

### 오전 보완 9명 Dry-Run 분석
- **#100 강수민 (링키영어 대원포레나점):** **WOULD-BE-EVALUATABLE** (유일한 승격 대상)
  - 예상 점수: **61점** (정체성 35 + 지도/활동 7 + 지속성 5 + 전문성 5 + 검증 4 + 지역성 5)
  - 조건: 4/4 필드 충족, 2026-05-27 증빙 존재, 오프라인 활동 확인
- **나머지 8명 (#62, #91, #93, #96, #101, #102, #108, #109):** **STAY RESEARCH_PENDING** (교육철학 미비 또는 2026년 증빙 부재로 미달)

---

## 4. NEXT STEPS AT HOME (집 PC 작업 순서)

### STEP 1: 83명 Production Snapshot 반영 & Publication 35 생성
1. `GET /api/admin/chart/tools/scores/stale`로 snapshot stale 후보 확인
2. 83명 recalculated score에 대해 공식 `submitReview` 실행 (상태 `APPROVED`)
3. `rebuildNationwidePublication` (또는 `autoPublishNationwide`) 실행하여 Publication 35 빌드
4. 운영 DB `chart_score_snapshots` 및 API 점수 일치 확인 (#45=63, #41=43, #46=39 등)

### STEP 2: Candidate #100 승격 및 PUBLIC COUNT > 83 확장
1. `POST /api/admin/chart/tools/candidates/100/reevaluate?dryRun=true` 호출하여 dryRun PASS 확인
2. `POST /api/admin/chart/tools/candidates/100/reevaluate?dryRun=false` 호출하여 공식 `EVALUATABLE` 승격
3. Candidate #100에 대해 공식 `submitReview` → snapshot 생성
4. `rebuildNationwidePublication` 실행하여 Publication 빌드 (예상 public count = 84명)

### STEP 3: PUBLIC COUNT > 83 및 `/chart` 랜더링 검증
1. public rankings API (`GET /api/chart/rankings/nationwide`) 조회하여 total count 84명, rank 1~84 연속 확인
2. 실제 홈페이지 `https://feel2real.co.kr/chart` 접속하여 84위 강수민 선생님까지 정상 표시되는지 확인

### 💡 Troubleshooting Guide:
- 만약 publication count 및 API 결과는 84명이나, 실 홈페이지 UI에서 83명만 표시되는 경우:
  - 프론트엔드 pagination / hardcoded slice / API limit / rendering limit (`slice(0, 83)`) 로직을 조사하여 84위 이하 랜더링 차단 원인 수정.

---

## 5. SAFETY INVARIANTS

- ❌ candidate eligibility 직접 SQL `UPDATE` 금지
- ❌ candidate score 직접 SQL `UPDATE` 금지
- ❌ 26명 일괄 강제 EVALUATABLE 승격 금지
- ❌ production DB 직접 초기화/삭제 금지

---

## 6. STEP 1 RESULTS — 2026-09-19 HOME VERIFICATION (COMPLETE)

**STEP 1 STATUS: COMPLETE — 목표 상태는 이미 운영에 반영되어 있었음. 이번 세션에서 쓰기 작업 0건.**

### 6.1 ROOT CAUSE (handoff stale 기록)

이 문서 §2의 "snapshot 미반영, 홈페이지는 기존 snapshot 표시" 및 §4 STEP 1의 "Publication 35 생성 필요" 기록은 **오래된 상태**였다.
학원 PC가 handoff 문서 push(9/19 19:12 KST) 이전인 **9/19 13:19 KST(04:19 UTC)** 에 이미 STEP 1을 실행 완료한 상태였다:

- Review Batch **197** (`region=seocho`, summary: "2026-09-19 83명 점수 재계산 반영 (공식 산식 재적용)")
  - submittedAt `2026-09-19T04:19:25` → decision **APPROVED** at `04:19:36` (snapshot 83건 review_status=APPROVED)
  - Batch 197의 candidateId 83개 집합 == recalc CSV 83개 id 집합 **완전 일치** (차집합 0)
- **Publication 34** 가 해당 최신 snapshot 기반으로 `04:19:43` 에 생성됨 (`updatedAt=2026-09-19T04:19:43`)
- 이전 Publication 33은 Batch 196(gyeongnam-gimhae, 9/14) 기반

→ 결론: Publication 번호 35는 불필요. **Publication 34가 이미 83명 재계산 최신 snapshot을 반영 중.**

### 6.2 HOME 검증 결과 (전부 READ-ONLY, 2026-09-19 저녁)

PRE-FLIGHT (public API):
- `GET /api/chart/publication/current`: publicationId=**34**, entries=**83**, discovered=0, rank 1~83 연속
- `GET /api/chart/rankings`: total=**83**

SNAPSHOT FRESHNESS (ADMIN 세션, `GET /api/admin/chart/tools/scores/stale`):
- **stale = 0건** → 83명 전원의 최신 snapshot이 최근 data change 이후 생성된 상태
- #45/#41/#46: snapshot review_status=APPROVED, score 63/43/39 (Batch 197 소속 확인)

SCORES (`publication/current` live vs `docs/chart/research/2026-09-18/01_chart_recalc_83.csv` recalc_score 전수 대조):
- **#45=63, #41=43, #46=39** ✓
- score=17: **25명** ✓ / avg: **45.00** ✓ / min=17, max=95 ✓
- **83/83 live == recalculated, mismatch 0** ✓

WRITE ACTIONS (이번 홈 세션):
- score update: **NONE**
- review batch: **NONE**
- publication rebuild: **NONE**

### 6.3 인증 방식 (운영 ADMIN API 접근 기록)

- `/api/admin/chart/tools/**` 중 stale/decision/finalize/rebuild는 **ROLE_ADMIN JWT 쿠키(`ACCESS_TOKEN`) 전용** — 브라우저 ADMIN 세션에서 동일 출처 `fetch(credentials:'include')`로 호출 (Secret 미사용·미로그)
- `CHART_AGENT_TOKEN` Bearer allowlist에는 `POST /review-batches` 등 조사 파이프라인만 포함 — 이번 검증에는 불필요

### 6.4 다음 작업 (STEP 2)

STEP 1 완료 확정. 이제 §4 STEP 2 (#100 강수민 EVALUATABLE 승격 → 84명 확장) 진행 가능.
- `POST /api/admin/chart/tools/candidates/100/reevaluate?dryRun=true` → PASS 확인 후 `dryRun=false`
- 승격 후 #100 포함 `POST /review-batches` → decision APPROVED → `POST /publications/rebuild` (이때는 실제로 rebuild 필요, Publication 35 예상)
- STEP 3: public count 84 / rank 1~84 연속 / `feel2real.co.kr/chart` UI 84위 표시 검증

