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

