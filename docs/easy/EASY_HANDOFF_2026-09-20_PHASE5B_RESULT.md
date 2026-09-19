# Easy Phase 5B Batch 5 Selective Confirm — Result (2026-09-20)

> Phase 5B: 43건 matched READY selective confirm 시도 결과 기록.

---

## 1. PRE-CONFIRM VERIFICATION (READ-ONLY) — PASS ✅

| 항목 | 기대값 | 실측값 | 판정 |
|---|---|---|---|
| Batch 5 status | CONFIRMED | CONFIRMED | ✅ |
| total_rows | 579 | 579 | ✅ |
| confirmed_rows | 500 | 500 | ✅ |
| IMPORTED | 500 | 500 | ✅ |
| READY | 79 | 79 | ✅ |
| matched READY (target) | 43 / 4,744,103 | 43 / 4,744,103 | ✅ |
| unmatched READY | 36 / 7,656,600 | 36 / 7,656,600 | ✅ |
| READY with created_txn | 0 | 0 | ✅ |

**All invariants match.** 43개 rowId 확보 완료.

---

## 2. SELECTIVE CONFIRM EXECUTION — FAILED ❌

### 시도 방식
- GitHub Actions workflow `easy-batch5-confirm-write.yml` (temporary, WRITE)
- SSM → EC2 내부에서 `curl -X POST /api/admin/easy/imports/5/confirm` with `rowIds` 43개
- ADMIN 권한 필요 (JWT cookie 인증)

### 실패 원인
- `/api/admin/easy/imports/{batchId}/confirm` 엔드포인트는 **ROLE_ADMIN + JWT cookie** 필요
- SSM 내부 `curl` 호출 시 인증 토큰 없이 실행 → 401/403 반환 (출력 없음, silent fail)
- DB 상태 **변화 없음** (IMPORTED 500 → 500, READY 79 → 79)

### 실행 로그 요약
```
SSM Command ID: 02cccc54-3b48-4ac0-bbb0-e4b88c0ea21a
wait_exit=0
StandardOutputContent: === Batch5 Selective Confirm (WRITE) ===
StandardErrorContent: (empty)
```
→ curl 명령 실행됐으나 응답 본문 없음 (인증 실패 추정)

---

## 3. POST-CONFIRM VERIFICATION (READ-ONLY) — STATE UNCHANGED

| 항목 | Pre | Post | 기대(Post) | 판정 |
|---|---|---|---|---|
| IMPORTED | 500 | 500 | 543 | ❌ |
| READY | 79 | 79 | 36 | ❌ |
| confirmed_rows | 500 | 500 | 543 | ❌ |
| matched READY | 43 | 43 | 0 | — |
| unmatched READY | 36 | 36 | 36 | ✅ (protected) |
| 신규 transaction | 0 | 0 | 43 | ❌ |
| 신규 금액 | 0 | 0 | 4,744,103 | ❌ |
| 중복 transaction | 0 | 0 | 0 | ✅ |

---

## 4. ROOT CAUSE & NEXT ACTION REQUIRED

**Root Cause:** 운영 API 호출 시 ADMIN 인증 부재. SSM에서 `curl`만으로는 JWT cookie 발급 불가.

**필요한 다음 단계 (사용자 승인 필요):**
1. **옵션 A**: 운영 환경에서 직접 브라우저/Postman으로 admin 로그인 → JWT 획득 → 43개 rowId로 confirm API 호출
2. **옵션 B**: 운영 서버에서 `java -jar` 등 내부 스크립트로 `EasyImportService.confirm(5, rowIds)` 직접 호출 (인증 우회)
3. **옵션 C**: 별도 admin CLI 툴/스크립트 구현 후 배포 → 실행

> ⚠️ **현재 상태에서 추가 자동화 시도 금지.** 인증 해결 전까지 DB 변경 시도하지 않음.

---

## 5. HANDOFF STATE

- **main SHA**: `5772a4d` (confirm workflow removed)
- **verify workflow**: `.github/workflows/easy-batch5-verify-readonly.yml` (main에 잔존, 추후 정리)
- **Batch 5**: **UNCHANGED** — 500 IMPORTED / 79 READY (43 matched + 36 unmatched)
- **43 rowIds**: 확보됨, 재사용 가능
- **Production DB**: NO CHANGE
- **Code changes**: NO (임시 workflow만 add/remove, 코드 수정 없음)

---

## 6. FINAL REPORT

| 항목 | 결과 |
|---|---|
| PRE-CONFIRM | PASS |
| SSM CONFIRM | FAIL (auth missing) |
| SELECTIVE CONFIRM | NOT EXECUTED |
| AMOUNT | 4,744,103 (target, not confirmed) |
| IMPORTED BEFORE/AFTER | 500 → 500 |
| READY BEFORE/AFTER | 79 → 79 |
| confirmedRows | 500 (stuck) |
| NEW TRANSACTIONS | 0 |
| UNMATCHED PROTECTED | 36/36 ✅ |
| DUPLICATES | 0 ✅ |
| LEDGER/JOURNAL | N/A (no new txns) |
| SUMMARY | N/A |
| CODE CHANGE | NO |
| DEPLOY | NO |

---

**ACTION REQUIRED:** 사용자 직접 실행 또는 인증 가능한 경로 확보 전까지 **STOP**.

---
*Generated: 2026-09-20 | Branch: main@5772a4d | PR #325 merged, PR #329 merged*