# EASY Academy → Home Handoff
Date: 2026-09-24

## 1. Repository
- repo: woori73/feel2real-buildit
- branch: feat/easy-kb-local-collector-poc (base: b83e4215 = PR #399 HEAD)
- PR #399 (feat/easy-kb-card-real-source-e2e): OPEN, CI PASS — DO NOT MODIFY, separate branch used
- This branch PR: created at close (checkpoint only, NO MERGE)

## 2. Today's Goal
KB국민카드 실제 자동수집 2차 실험 (yesterday: Shinhan reference — do NOT redo)

## 3. Shinhan Reference
- ShinhanCardExcelParser / Normalizer / ImportService: PASS, preserved on origin/main
- Pattern reused for KB (dedicated KB classes, no Shinhan branching)

## 4. KB Round 2 (evidence only)
- KB site entry: PASS (user, card.kbcard.com)
- login: PASS (user)
- certificate: PASS (user, 개인사업자용, password never recorded)
- user authentication: PASS (extra auth NONE)
- agent resume (real site): NOT IMPLEMENTED (mock PoC only)
- transaction search: PASS (user, My KB → 개인사업자용 이용내역)
- September date range: PASS (2026-09-01 ~ 2026-09-24, single query, 50 rows)
- download: PASS (user, official Excel, real BIFF .xls)
- downloaded file: C:\Users\VIEW LIFE\Downloads\KB*이용내역*_20260901_20260924.xls (46080 bytes, SHA256 6999C5DB…0903587C1B2A5)
- collector detection: NOT IMPLEMENTED (file sits in Downloads, no watcher)
- parser: PASS (KbCardExcelParser, 14-col validation, fail-closed status)
- normalize: PASS (KbCardNormalizer, no FX, USD cents)
- dedupe: PASS (unit test; real-file import NOT run)
- staging: NOT IMPLEMENTED (DB gate never opened)

## 5. Downloaded File
- local path: C:\Users\VIEW LIFE\Downloads\KB*이용내역*_20260901_20260924.xls
- filename: KB 이용내역_20260901_20260924.xls (exact name garbled in console; identify by size+hash)
- format: real MS BIFF .xls (OLE2), 1 sheet, 57 rows (7 summary + 50 data)
- size: 46080, modified: 2026-09-24 02:37
- schema: 이용일/이용시간/이용고객명/이용카드명/이용하신곳/국내이용금액(원)/해외이용금액($)/결제방법/가맹점정보/할인금액/적립(예상)포인트리/상태(전표매입49·전표미매입1)/결제예정일/승인번호
- HOME TRANSFER: home PC has NO copy. Transfer via USB/private channel only. NEVER to public GitHub.

## 6. Code Changed Today
- local-collector/src/workflows/kbCardWorkflow.js (NEW): KB_CARD declarative workflow (login URL, auth marker, date fill, search, Excel download)
- local-collector/src/workflows/index.js: registry entry
- local-collector/mock-site/server.js: /kb-download.xls mock route (fake bytes)
- local-collector/mock-site/public/kb-login.html, kb-usage.html (NEW): KB-shaped mock pages
- local-collector/test/e2e-kb-lifecycle.js (NEW): KB COLLECTOR LIFECYCLE: PASS (visible Chromium)
- local-collector/package.json: test:e2e-kb script
- local-collector/.gitignore: .e2e-kb-downloads/
- src/main/.../easy/service/KbCollectorIngestService.java (NEW): integrity gate + KbCardImportService bridge
- src/test/.../KbCollectorIngestServiceTest.java (NEW): 50-row synthetic, 2nd-run 0 new, tamper rejects (NOT yet executed green — compileTestJava was still running at close)

## 7. Tests
- EXECUTED: `npm run test:e2e-kb` → PASS (auth wait, same-session resume, dates, download, sha256 integrity, COMPLETED)
- EXECUTED: compileJava UP-TO-DATE (PASS, no errors)
- NOT COMPLETED: KbCollectorIngestServiceTest (Gradle test worker still in compileTestJava at close; DO NOT assume PASS)
- From #399 (already on base): KB parser/normalizer/import 15/15 PASS, Shinhan regression + classification + accounting PASS, bootJar PASS, PR #399 CI PASS

## 8. Known Problems
- KbCollectorIngestServiceTest never got a green run (machine contention: 8 other JVMs). First home action: run it alone.
- Real-file import to local DB never executed (needs DB gate decision at home).
- Cancellation representation still UNKNOWN (0 cancel rows in real file).
- Real KB DOM selectors in kbCardWorkflow are integration-contract defaults; first real-site run may need adjustment (fail-closed).

## 9. HOME FIRST STEP
```
git fetch origin --prune
git worktree add -b feat/easy-kb-local-collector-poc <path> origin/feat/easy-kb-local-collector-poc
# read docs/easy/EASY_HANDOFF_2026-09-24_ACADEMY_TO_HOME.md
# then: ./gradlew test --no-daemon --console=plain --tests ...KbCollectorIngestServiceTest
```

## 10. DO NOT REDO
- Shinhan parser/verification (done yesterday, on main)
- KB manual login/download (file already secured; home needs file transfer, not re-download)
- KB parser/normalizer/import unit tests from #399 (green)
- Node e2e-kb mock lifecycle (green)
