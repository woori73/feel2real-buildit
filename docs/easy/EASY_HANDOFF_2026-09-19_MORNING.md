# Easy Accounting Morning Handoff — 2026-09-19

> 집 PC(Home PC) 작업 종료 시점 인계 문서. 학원 PC(Academy PC)에서 이어서 작업함.

---

## 1. GITHUB STATE

**Repo:** woori73/feel2real-buildit  
**Base main SHA:** `47fa9a917e25092d32d0604bfd2add2f96e35efc`  
**Feature branch:** `feature/easy-v979-v980-reclassify-20260919`  
**PR:** 생성 예정 (merge 금지)

---

## 2. PHASE 2 INVESTIGATION SUMMARY

### batchId=5 현황 (from PR #316/317 handoff)
| 구분 | 건수 | 금액 | 비고 |
|---|---|---|---|
| Originally staged | 579 | 12,400,703 + 41,728,700 | V978 staging 시점 |
| Confirmed (immutable) | 500 | — | **절대 수정 금지** |
| Remaining READY/REVIEW | 79 | 12,400,703 | 이번 작업 대상 |

### 79건 내부 구성
| Category | Rows | Amount | Status |
|---|---|---|---|
| **CONFIDENT 43 (target)** | **43** | **4,744,103** | 재분류 → confirm 예정 |
| 전화/휴대폰/인터넷 | 2 | 804,700 | V979 Golden mapping |
| 서비스 이용료 | 2 | 67,900 | V979 Golden mapping |
| 이마트24 차.음료 | 18 | 70,300 | V980 Classification rule |
| 거래처 선물 | 21 | 3,801,203 | V979 Golden mapping |
| **Untouched REVIEW 36** | **36** | **7,656,600** | **절대 수정 금지** |
| NEED_USER_DECISION | 9 | 127,900 | 사용자 판단 대기 |
| POSSIBLE_PRIVATE/NONDEDUCTIBLE | 27 | 7,528,700 | 개인성 가능성, 자동 분류 금지 |

**산술 검증:** 43+36=79 ✅ | 4,744,103+7,656,600=12,400,703 ✅

---

## 3. PHASE 2.6 구현 내용

### 3.1 V979 Migration
**파일:** `src/main/resources/db/migration/V979__add_easy_communication_account_and_mappings.sql`

| 항목 | 값 |
|---|---|
| 신규 계정 | `EXPENSE_COMMUNICATION` — 통신비 — EXPENSE — sort_order 23 |
| Golden mappings (3) | |
| 전화/휴대폰/인터넷 | → EXPENSE_COMMUNICATION |
| 서비스 이용료 | → EXPENSE_FEE (기존) |
| 거래처 선물 | → EXPENSE_ENTERTAIN (기존, V978) |
| **차.음료 global mapping** | **없음 (의도적 제외)** |

### 3.2 V980 Migration
**파일:** `src/main/resources/db/migration/V980__add_emart24_expense_classification_rule.sql`

| 항목 | 값 |
|---|---|
| Classification Rule | |
| pattern_type | COUNTERPARTY_REGEX |
| pattern | '이마트24' |
| account_id | EXPENSE_SUPPLIES (소모품) |
| priority | 50 |
| direction | EXPENSE |
| active | TRUE |

**설계 원칙:**
- 차.음료 global mapping 추가 안 함
- 스타벅스/CU 등 다른 차.음료 영향 없음
- EXPENSE 방향만 적용 (INCOME 제외)
- append-only, idempotent NOT EXISTS
- 기존 rule/mapping 불변

### 3.3 코드 변경

| 파일 | 변경 |
|---|---|
| `EasyImportRowRepository.java` | `findByBatch_BatchIdAndRowIdIn()` 추가 |
| `EasyImportService.java` | `reclassify(Long batchId, List<Long> rowIds)` 추가 |
| `EasyImportController.java` | `POST /api/admin/easy/imports/{batchId}/reclassify` 추가 |

**reclassify() 동작:**
- 명시된 `rowIds`만 처리 (batch 전체 자동 처리 안 함)
- READY 상태만 처리, CONFIRMED/IMPORTED/DUPLICATE/ERROR 건너뜀
- `createdTxn != null`이면 건너뜀 (이미 확정된 행 보호)
- `resolveMapping()` → `accountService.classify()` 공식 경로 재사용
- transaction 생성 안 함, confirm 안 함
- `matchedAccount`, `confidence`만 `markReady()`로 갱신

---

## 4. 테스트 결과 — ALL PASS ✅

| Test Suite | Tests | Status |
|---|---|---|
| `EasyImportServiceTest` | 15 (신규 7 reclassify) | PASS |
| `EasyV979CommunicationAccountTest` | 12 (V979 4 + V980 6) | PASS |
| `EasyExpenseGoldenMappingTest` | 3 (V978 회귀) | PASS |
| `EasyLedgerDeleteTxnRegressionTest` | 3 | PASS |
| `EasyLedgerServiceTest` | 15 | PASS |
| `EasyReportServiceTest` | 12 | PASS |
| **All Easy domain** | **60+** | **PASS** |

**빌드:** `compileTestJava` ✅ | `bootJar` ✅

### 핵심 검증 테스트
- `reclassify_all43CategoriesMapped()`: 4개 카테고리 동시 재분류 → 모두 matchedAccount 설정됨
- `reclassify_emart24RowToSupplies()`: 이마트24 1행 → EXPENSE_SUPPLIES (confidence 0.9)
- `starbucksBeverageNotClassified()`: 스타벅스 차.음료 → 매칭 안 됨 (REVIEW 유지)
- `genericBeverageNotClassified()`: CU 차.음료 → 매칭 안 됨
- `emart24IncomeNotClassified()`: INCOME 방향 이마트24 → 매칭 안 됨
- `noGlobalBeverageMapping()`: 차.음료 global mapping 없음 확인

---

## 5. batchId=5 상태 (불변)

| 항목 | 값 |
|---|---|
| 500 confirmed | **절대 수정 금지** (기존 transaction, fingerprint, journal 모두 보존) |
| 79 remaining | 12,400,703 원 |
| target 43 | 4,744,103 원 |
| untouched 36 | 7,656,600 원 |

> ⚠️ **주의:** 41,728,700은 별도 P&L/연간 비용 맥락의 NET EXPENSE 숫자임. batchId=5의 500 confirmed amount와 혼동하지 말 것.

---

## 6. 43 TARGET BREAKDOWN

| Category | Rows | Amount | Expected Account | Resolution |
|---|---|---|---|---|
| 전화/휴대폰/인터넷 | 2 | 804,700 | EXPENSE_COMMUNICATION | V979 resolveMapping |
| 서비스 이용료 | 2 | 67,900 | EXPENSE_FEE | V979 resolveMapping |
| 이마트24 차.음료 | 18 | 70,300 | EXPENSE_SUPPLIES | V980 classify fallback |
| 거래처 선물 | 21 | 3,801,203 | EXPENSE_ENTERTAIN | V979 resolveMapping |
| **TOTAL** | **43** | **4,744,103** | | |

---

## 7. PRODUCTION ROW_IDS — PENDING

**이유:** 집 PC에서 Docker daemon 미가동으로 로컬 DB(`localhost:3308/f2r_db`) 접근 불가.

**학원 PC에서 수행할 READ-ONLY 조회:**
```sql
SELECT 
  r.row_id, r.row_no, r.occurred_on, r.counterparty, r.description,
  r.amount, r.external_account, a.code AS matched_account_code,
  JSON_UNQUOTE(JSON_EXTRACT(r.raw_data, '$[0].sourceRowNo')) AS source_row_no
FROM easy_import_rows r
LEFT JOIN easy_accounts a ON r.matched_account_id = a.account_id
WHERE r.batch_id = 5 AND r.status = 'READY'
ORDER BY r.row_no;
```

**예상 결과:** 79 rows → 위 43/36 breakdown과 일치 확인

---

## 8. 절대 실행하지 않은 것 (SAFETY)

- ❌ Production DB 직접 UPDATE/DELETE/INSERT
- ❌ Production reclassify API 호출
- ❌ Production confirm (43건 포함)
- ❌ Deploy (GitHub Actions 수동 트리거 안 함)
- ❌ PR merge
- ❌ batchId=5 삭제 또는 restage
- ❌ 500 confirmed 거래 수정
- ❌ 36 REVIEW 행 수정
- ❌ 차.음료 global mapping 추가
- ❌ 개인 의료/카드/계좌/credential GitHub 업로드

---

## 9. 기타 작업 보호

- 다른 worktree/branch/stash (`content/shingu3-round4-q11-q20` 등) **건드리지 않음**
- 이 작업은 Easy 전용 feature branch에서만 수행

---

## 10. 학원 PC에서 이어서 할 작업 (NEXT)

1. **Docker 가동** → 로컬 DB 또는 운영 READ-ONLY 접속
2. **batchId=5 79 READY rows 조회** → 정확한 43 rowIds 산출
3. **43 rowIds로 재분류 시뮬레이션** → 로컬에서 `reclassify()` 실행 검증
4. **43 rowIds로 partial confirm 시뮬레이션** → journal/P&L 검증
5. **결과 이상 없으면** → PR 리뷰 요청 → merge → deploy → production 실행

---

## 11. FILES CHANGED IN THIS SESSION

### New Migrations
- `src/main/resources/db/migration/V979__add_easy_communication_account_and_mappings.sql`
- `src/main/resources/db/migration/V980__add_emart24_expense_classification_rule.sql`

### Source Code
- `src/main/java/.../controller/EasyImportController.java`
- `src/main/java/.../repository/EasyImportRowRepository.java`
- `src/main/java/.../service/EasyImportService.java`

### Tests
- `src/test/java/.../service/EasyImportServiceTest.java` (기존 + 7 new)
- `src/test/java/.../service/EasyV979CommunicationAccountTest.java` (new, 12 tests)

### Documentation
- `docs/easy/EASY_HANDOFF_2026-09-19_MORNING.md` (this file)
- `PHASE2_BATCH5_PREVIEW_REPORT.md` (working reference)

---

**END OF HANDOFF** — Ready for Academy PC continuation.