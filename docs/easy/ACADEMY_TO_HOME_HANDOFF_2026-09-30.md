# ACADEMY → HOME HANDOFF (EASY, 2026-09-30)

> 학원 PC 작업 마감. 새 작업 시작 금지 상태로 인계한다.
> HOME에서는 처음부터 조사하지 말고 아래 순서대로 이어간다.

## 1. BRANCH / HEAD

- HANDOFF BRANCH: `docs/easy-academy-to-home-20260930` (본 문서만 포함, main 기반)
- MAIN TIP (인계 시점): `bed06852` (PR #446 merge)
- EASY 작업 worktree: `feel2real-buildit-kbprep` → detached HEAD `b83e4215`
  (`feat/easy-kb-card-real-source-e2e`, unpushed 변경 없음)
- `feel2real-buildit-easy445` worktree: `fix/easy-v1010-...` (병합済, 원격 브랜치 삭제済)
- WORKTREE STATUS: tracked 수정 없음. 유일한 untracked =
  `feel2real-buildit-kbprep/.../domain/easy/service/KbRealFileScratchTest.java`
  (아래 §6, 로컬 QA 도구 — 공유 브랜치 오염 방지 위해 push하지 않음)

## 2. 오늘 완료 (ACADEMY)

- SOURCE RECOVERY CENSUS (6원천, 읽기 전용)
- KB CARD 원본 발견 + 실파서 normalize 대사 완료 (값은 §5, HOME 재확정 필요)
- Shinhan card 과거 evidence 확인 (원본 소실 — §5)
- CREFIA 상태 (수집이력 전무 — §5)
- HOMETAX 상태 (월별 요약만 — §5)
- C110700 발견 (정체 미확정, 삭제 금지 — §7)
- BATCH5 DEFERRED 유지 (36건 untouched, confirm 실행 없음)

## 3. PRODUCTION 상태 (인계 시점)

- PR #445 MERGED (`93b8734a`), PR #446 MERGED (`bed06852`), CI PASS
- 자동배포 SUCCESS (V1014 적용 확인 — `flyway_schema_history` 최상단)
- 1차 배포 실패 원인: V1010 out-of-order (prod에 V1011~V1013 기적용) →
  미적용 파일 번호만 V1010→V1014로 이동, 내용 동일. RDS 직접 수정 없음.
- Batch5: READY 36 / 7,656,600 (그대로 보존), IMPORTED 543
- 장부: 학원 657건(수입 61,186,000/지출 70,902,803), 분개 balanced
- PRODUCTION WRITE TODAY: `POST /imports/5/prepare` 1회 (승인됨).
  PENDING 0건이라 상태 전이 없는 no-op임을 응답(READY 36/IMPORTED 543 유지)으로 확인.
  confirm/ingest/CSV 업로드 실행 없음. LOCAL :3308 미접촉. 직접 SQL 없음.

## 4. LOCAL-ONLY SOURCE FILE (GitHub 업로드 금지)

금융·개인정보 포함 가능. 절대 commit하지 말 것.
HOME PC에 자동 존재하지 않음 — 사용자가 안전하게 옮기거나 HOME에서 재다운로드.

- `C:\Users\VIEW LIFE\Downloads\카드이용내역_20260901_20260924.xls`
  (46,080 bytes, KB국민 비씨카드, 일시불) — PRESERVED, 미수정
- `C:\Users\VIEW LIFE\Downloads\20260926.C110700.01`
  (114,556 bytes, 578 records 은행 인터페이스 파일) — PRESERVED, 미수정

## 5. SOURCE RECOVERY CENSUS 요약

| SOURCE | 상태 |
|---|---|
| KB CARD | **RECOVERABLE NOW**. 원본 위 §4 + 파서 브랜치 + 테스트 15/15. LOCAL :3308 50건은 테스트 복사본 (운영 복사 금지, evidence 대조용) |
| SHINHAN CARD | 파서 main 병합済. 원본 `개인사업마이신한내역download.xls` 소실 (09-23 QA 시 29건·합계 1,026,892원 존재 확인, `qa_shinhan.py` 참조). MISSING_LOCAL_SOURCE |
| SHINHAN/KB BANK | 수집이력 전무. 통장 파일 없음 |
| CREFIA | 어댑터 골격+fail-closed 가드만. connection/runs 0. 수집결과 없음 |
| HOMETAX | `taxRsltMng20260929*.xls` 2건 = 월별 요약(28행, 2026.1~12) — 거래내역 아님. 거래레벨 0 |

## 6. KB 50건 — ACADEMY 실측값 (HOME 재확정 필요)

- 원본: §4 KB xls. 범위 2026-09-03~09-23, 데이터행 50
- 실파서(`KbCardExcelParser`+`KbCardNormalizer`, businessId=1) 실행 결과:
  RAW=50 NORMALIZED=50, drop 0, parse error 0
- 국내 KRW 49건 합계 **1,118,503원** (xls 직접계산과 일치)
- 해외 USD 1건 $19.81 (1981¢, 09-16, OPENAI 구독) — FX 환산 없음 계약대로 보류 대상
- 승인번호 50건 전부 존재 (fingerprint dedupe 가능)
- 실행 방법: kbprep worktree의 `KbRealFileScratchTest` (DB 없음, Spring 없음).
  산출물 `build/kb-real-dump.txt` (gitignored, 파이프 구분 13컬럼).
  Gradle 결과: **BUILD SUCCESSFUL** (정체 아님 — 인계 지시의 HUNG 전제는 구 정보).
- 09/17~09/23 구간·EASYSHOP dedupe·사업장/계정 분류·ingest는 **미착수** (HOME 순서 §8 B~H)

## 7. C110700 (HOME READ ONLY 분석 필요)

- 고정길이 은행 인터페이스 파일로 보임 (레코드 타입 51/52/53/92, 소유자 식별자 포함)
- 출금이체 신청/결과 또는 CMS 파일 추정 — **판정 UNKNOWN**
- PII 마스킹 보고, 은행 추측 확정 금지. 정체 확정 전 ingest 금지

## 8. HOME FIRST ACTION (순서 준수)

1. `git fetch origin`
2. main tip `bed06852` 확인 + 본 브랜치(`docs/easy-academy-to-home-20260930`) 확인
3. 별도 worktree에서 본 문서 읽기
4. KB XLS 원본 확보 (위 §4 — 없으면 사용자 전달 또는 HOME 재다운로드)
5. 50건 normalize 대사 (위 §6 재실행·재확정)
6. EASYSHOP/MANUAL dedupe → 09/17~09/23 신규 확정 → 사업장/계정 분류
   → 불확실한 것만 REVIEW → 기존 운영 import pipeline → LIVE 대사
7. 신규 adapter 개발 금지. BATCH5 36건 confirm 금지 (DEFERRED 유지)

## 9. 계속 보류

- BATCH5 36: HISTORICAL PUBLISHING EXPENSE / USER DEFERRED / NO ACTION
- Shinhan card: 원본 없이 숫자만으로 ingest 금지
- 양 은행/CREFIA/Hometax: 회수 결과 보존, 필요한 원천만 확보
