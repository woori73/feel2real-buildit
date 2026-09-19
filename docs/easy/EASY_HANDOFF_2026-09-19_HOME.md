# Easy Accounting Batch 5 Selective Confirm Home Handoff — 2026-09-19

> 학원 PC(Academy PC) 및 집 PC(Home PC) 간 작업 인계 및 선택적 확정(Selective Confirm) 보존 문서입니다.

---

## 1. GITHUB & BRANCH STATE

- **Base Main SHA:** `80ffbddef10ba88f5a60b33d303da4b4667a8f46`
- **Handoff Branch:** `easy/batch5-confirm-handoff-20260919`
- **Latest Commit SHA:** `33901f894172f3d5fb6ebbd56b8fa104f22754ee`
- **Modified & Added Files:**
  - `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyImportService.java`
  - `src/test/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyImportServiceTest.java`
  - `src/test/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyBatch5DryRunVerificationTest.java`
  - `docs/easy/EASY_HANDOFF_2026-09-19_HOME.md`

---

## 2. MODIFICATION DETAILS & RATIONALE

### 변경 내용
1. **EasyImportService.java:**
   - `confirm()` 메소드에서 `CONFIRMED` 상태의 배치 처리 허용.
   - `rowIds` 지정을 통한 Selective Confirm 지원.
   - `matchedAccount == null`인 행 skip 처리로 unmatched READY 36건 보호.
2. **EasyImportServiceTest.java:**
   - 기존 fixture 테스트 중 `markReady(null, 0.0)`으로 되어 있던 부분을 실제 데이터 모델에 맞게 valid `matchedAccount`(`telecom`)로 수정하여 테스트 의도 보존.
   - Selective Confirm, CONFIRMED 배치 confirm, 미선택 READY 행 보호, unmatched READY 행 보호, 기존 500건 IMPORTED 행 보호 검증 unit test 4건 추가.
3. **EasyBatch5DryRunVerificationTest.java (신규):**
   - Batch 5 산술 검증 및 선택적 확정 동작을 READ-ONLY / DRY-RUN 관점에서 프로그램적으로 검증하는 테스트 추가.

---

## 3. BATCH 5 DATA INVARIANTS

| 구분 | 건수 | 금액 | 상태 및 처리 방침 |
|---|---|---|---|
| **Total Staged Rows** | **579** | — | V978 최초 업로드 총 건수 |
| **Confirmed / Imported** | **500** | — | **IMMUTABLE (절대 수정/재확정/삭제 금지)** |
| **Remaining READY** | **79** | **12,400,703 원** | 현재 선택 확정 작업 대상 |
| **Classified READY (Target)** | **43** | **4,744,103 원** | V979/V980 적용 완료된 4종 카테고리 (전화/인터넷 2건, 이용료 2건, 이마트24 18건, 선물 21건) → Confirm 대상 |
| **Unmatched READY (Untouched)** | **36** | **7,656,600 원** | NEED_USER_DECISION (9건) / POSSIBLE_PRIVATE (27건) → **UNTOUCHED (절대 자동 분류/확정 금지)** |

**산술 검증:** 43건 + 36건 = 79건 ✅ | 4,744,103원 + 7,656,600원 = 12,400,703원 ✅ | 500건 + 79건 = 579건 ✅

---

## 4. TEST RESULTS & OBSERVATIONS

- `./gradlew test --tests "*Easy*"` 실행 결과: **62 tests completed, 0 failed (ALL PASS)** ✅
- `EasyImportServiceTest` mock fixture 수정 완료 및 회귀 테스트 전체 통과.
- `EasyBatch5DryRunVerificationTest` 실행 완료: READ-ONLY / DRY-RUN 관점에서 43건 target row 확정, 500건 기존 확정 보존, 36건 unmatched READY 보존 검증 완료.

---

## 5. PRODUCTION STATUS & SAFETY INVARIANTS

- **Production Confirm 실행 여부:** **미실행 (NO)** — 운영 DB 변경 없음, Confirm API 호출 안 함.
- **Deploy 여부:** **NO**
- **Safety Invariants:**
  1. `500건 IMPORTED` 행의 기존 장부 거래(Transaction), 지문(Fingerprint), 분개(Journal)는 절대 수정하거나 삭제하지 않는다.
  2. `36건 unmatched READY` 행은 사용자의 명시적 결정 전까지 `READY` 상태로 보존한다.

---

## 6. NEXT STEPS AT HOME (집 PC 작업 순서)

1. `git fetch origin` 및 `git checkout easy/batch5-confirm-handoff-20260919`
2. `./gradlew test --tests "*Easy*"` 실행하여 전체 EASY 테스트 통과 재검증 (`62 tests completed, 0 failed`)
3. GitHub PR 생성 및 코드 리뷰 완료 후 main 브랜치 merge
4. 운영 배포 (수동 GitHub Actions)
5. 운영 환경에서 batchId=5 대상 43개 rowId selective confirm 실행:
   `POST /api/admin/easy/imports/5/confirm { "rowIds": [<43개 rowId>] }`
