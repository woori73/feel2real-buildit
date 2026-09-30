# 01 — 현재 EASY 아키텍처 (Import → Confirm → Journal 파이프라인)

> 조사일: 2026-09-20 (PHASE 0, 코드 변경 없음). 목적: 자동수집 레이어를 기존 파이프라인 **앞단**에 붙이기 위한 현재 구조 사실 정리.
> 기준 커밋: `116ceae` (main). 패키지: `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/` (이하 `<easy>`, 컨트롤러 9 / 엔티티 16+enum 8 / 리포 16 / 서비스 14 / DTO 32).

## 1. 데이터 모델 (Flyway `src/main/resources/db/migration`)

| 테이블 | 마이그레이션 | 엔티티 |
|---|---|---|
| `easy_businesses` | V973 (`V973__add_easy_ledger.sql:16-26`, ACADEMY/PUBLISHING 시드) | `EasyBusiness` |
| `easy_students` / `easy_tuition_payments` | V973:35-50 / :80-100 (UNIQUE student+business+target_month) | `EasyStudent` / `EasyTuitionPayment` |
| `easy_transactions` | V973:55-75 + V974:141-150 + V975:27-35 + V977:15-17 | `EasyTransaction` |
| `easy_accounts` | V974:20-31 (+SETTLEMENT V976:70, +V978, +V979) | `EasyAccount` |
| `easy_account_mappings` | V974:51-66, **UNIQUE(account_id, external_code)** | `EasyAccountMapping` |
| `easy_classification_rules` | V974:71-87 (첫 규칙 V980) | `EasyClassificationRule` |
| `easy_import_batches` | V974:92-106 | `EasyImportBatch` |
| `easy_import_rows` | V974:108-136 + V976:19-22 + V977:20-21, **UNIQUE(batch_id, row_no)** | `EasyImportRow` |
| `easy_review_items` | V975:43-70 (+MISSING_EXPENSE V977) | `EasyReviewItem` |
| `easy_withholding_records` / `easy_annual_closings` | V975:79-101 / :108-123 | |
| `easy_journal_entries` / `easy_journal_lines` | V976:27-67 (차변/대변 CHECK 제약) | `EasyJournalEntry/Line` |
| `easy_filing_references` / `easy_correction_logs` | V976:77-95 / :100-117 (append-only) | |

핵심 컬럼 요지:
- `easy_import_rows`: occurred_on, direction, amount, counterparty, description, pay_method, supply_amount, vat_amount, evidence_type, **raw_data(TEXT, 원본 행 JSON + `[srow:N]`/`[ref:...]`/`[menu:...]` 소스 표식)**, **external_account(V976, 이지샵 원문 계정명 보존)**, reversal, status(PENDING/READY/ERROR/IMPORTED/DUPLICATE), matched_account_id, confidence, created_txn_id.
- `easy_transactions` (V975 확장): **source_kind VARCHAR(20), source_ref VARCHAR(200), fingerprint VARCHAR(64)** — 출처 추적·중복의심 탐지 훅이 이미 존재. fingerprint 인덱스는 **비unique(의심 탐지용, 자동합치기 금지 설계)**.

## 2. Import 파이프라인 (현행 흐름)

**서버사이드 CSV/XLS 파싱은 없음.** CSV→JSON 변환은 오프라인 스크립트 `csv_to_json.py`(repo root)가 수행하며, API는 **JSON row 배열만** 받는다 → 자동수집 어댑터가 동일 JSON 페이로드만 생성하면 되기 때문에 **앞단 결합 지점으로 이상적**.

1. **업로드**: `<easy>/controller/EasyImportController.java`
   - `POST /api/admin/easy/imports` → 일반 로우 staging (:44-47)
   - `POST /api/admin/easy/imports/easyshop` → 이지샵 26필드 JSON (:53-57). `EasyShopImportRequest.EasyShopRow` record(:15-49) — javadoc(:41-48)에 **향후 자동수집용 externalRef를 별도 필드로 추가할 계획을 이미 명시**
   - `GET /api/admin/easy/imports[/{batchId}]`, `POST /{batchId}/confirm`, `DELETE /{batchId}`(폐기), `POST /{batchId}/reclassify`
2. **정규화·사업체 라우팅**: `EasyShopImportService.stage()`:53-101 — 최대 2000행, (business, sourceKind)별 배치 분할, 사업체 미확정 시 요청 전체 거부(추측 금지). normalize: sales→INCOME(externalAccount=`EASYSHOP:매출`), expense→음수금액이면 reversal(INCOME+reversal=TRUE), funds(입출금)→`EASYSHOP_FUNDS` 별도 로우.
3. **Staging+자동 계정 매핑**: `EasyImportService.stage()`:67-148 — 1단계 `resolveMapping(external_account)` exact 매칭(confidence 1.0), 2단계 규칙 `EasyAccountService.classify()`:170-203 (COUNTERPARTY_EXACT=1.0 / COUNTERPARTY_REGEX=0.9 / DESCRIPTION_REGEX=0.8, priority ASC, amount/direction 필터). 미매칭이면 matched_account=null READY.
4. **장부 중복 제외**: `EasyShopImportService.markLedgerDuplicates()`:107-127 — fingerprint가 기존 easy_transactions에 있으면 DUPLICATE 처리(confirm 불가).
5. **Reclassify**: 매핑/규칙 추가 후 READY 로우만 재판정(트랜잭션 생성 없음):271-311.
6. **Confirm** (EASY 운영 원칙 — HOLD 금지): `EasyImportService.confirm(batchId,rowIds)` — PENDING 자동 준비 후 READY 전부를 장부로 적재한다. matched면 분류 적재, 미분류면 REVIEW 적재(account NULL·`미분류`·NEEDS_REVIEW·REVIEW_REQUIRED, 분개 생략). `EasyTransaction` 생성(sourceKind=batch.sourceKind, sourceRef=`"<파일명>#orig-row-N"`, fingerprint) → row IMPORTED → **`journalService.repost(txn)` 동기 실행** → 배치 카운터는 IMPORTED 로우 재계수(**부분 누적 confirm 지원**). 장부 fingerprint 중복은 DUPLICATE 확정 제외. 원천 판정 대출·이체는 `NOT_INCOME`/`NOT_EXPENSE` 세무구분 (손익 제외, 목록 표시). 출판사 확정분은 출판사 귀속. `POST /{batchId}/prepare`는 적재 없는 PENDING→READY 미리보기.
7. **폐기**: STAGED 전용. 이미 IMPORTED된 로우는 되돌리지 않음(운영 데이터 불변 원칙).
8. **다운스트림**: `EasyJournalService.repost()`:47-77 (EASYSHOP_FUNDS 분개 제외; INCOME=Dr SETTLEMENT/Cr 계정, EXPENSE 반대), 결산·손익(`EasyReportService.yearPl`), 신고 대사(`filingCheck` vs `easy_filing_references`), 리뷰 큐 재빌드(`EasyAuditService.rebuild`:125-280, 6종: UNCLASSIFIED/EVIDENCE/WITHHOLDING/MISSING_EXPENSE/DUPLICATE_SUSPECT/IMPORT_UNCONFIRMED).

## 3. 상태 라이프사이클

- 배치: `STAGED → CONFIRMED` / `STAGED → DISCARDED`(종단). CONFIRMED 이후에도 잔여 READY 부분 confirm 가능.
- 로우: `PENDING → READY | ERROR`, `READY → IMPORTED | DUPLICATE`.
- 리뷰: `OPEN → RESOLVED | IGNORED` (resolve 시 txn 수정 + repost + correction log: `EasyAuditService`:286-386).
- 분개: `POSTED → VOIDED` (변경 시 void+repost).
- transactions 자체에는 status 없음(존재=확정). evidence_review/withholding_review는 소프트 플래그.

## 4. 현재 중복방지 수준 (자동수집 관점의 격차)

1. `(batch_id,row_no)` UNIQUE — 동일 배치 내만 보호.
2. fingerprint(SHA-256 of business+date+direction+amount+counterparty+description+pay_method) — **텍스트가 1자만 달라도 실패**. 재수집 멱등성 guarantee 없음.
3. **external transaction id에 대한 UNIQUE 제약 없음** (source_ref는 텍스트일 뿐). → 자동수집에서는 외부 ID 기반 dedupe가 신규 설계 필수.

## 5. 크론·보안·시크릿 현재 상태

- `/easy` 내 `@Scheduled` **전무**. 글로벌 `@EnableScheduling`은 켜져 있음(`PrivateTutorSolutionBackApplication.java:16`). 재사용 가능한 패턴: `ChartSiteToolService.java:222`(env 오버라이드 cron), `AlbAccessStatisticsWorker.java:57`(lease/heartbeat fixedDelay).
- 머신 대 머신 인증 선례 없음(선례 존재: `ChartAgentTokenFilter.java:50-59` — narrow agent-token allowlist). 자동수집 워커가 필요하면 이 패턴 차용 가능.
- `/api/admin/easy/**` 및 `/easy/**` 화면 모두 **ROLE_ADMIN 전용** (`SecurityConfig.java:325`, `:153-154`; 회귀테스트 `EasySecurityWebMvcTest`).
- 시크릿: 순수 env-var 플레이스홀더(`application.yaml:36-55`), 운영은 EC2 systemd `/etc/f2r/env` 주입(docs/AWS_DEPLOYMENT.md §6.4). **AWS SSM/Secrets Manager 미사용** (S3 sdk만 IAM Instance Profile로 사용).

## 6. 기존 문서에 명시된 자동수집 입장

- `docs/easy/AI_ACCOUNTING_ARCHITECTURE.md:86-90`: CODEF/은행/카드 스크레이핑, 자격증명 저장, 홈택스 자동화는 현재 **의도적 범위 밖(조사 대상)**. 금지사항(:103-108): 자동합치기 금지, 세율 추정 금지, 미검증 표준코드 사용 금지.
- `EASYSHOP_IMPORT_PLAN.md`: 원문 보존·추측 매핑 금지·배치 사업체 분리 원칙.
- `EASY_HANDOFF_2026-09-19_MORNING.md`: batchId=5 부분 전략(확정 500 불변), reclassify API, "확정 데이터 절대 수정 금지".

## 7. 자동수집 레이어가 기존 구조를 건드리지 않고 붙는 위치 (결론)

- **안정적 봉합면(seam)**: `POST /api/admin/easy/imports[/easyshop]` JSON 계약. stage 이후(매핑→READY/REVIEW→confirm→journal)는 그대로 재사용.
- 이미 준비된 프로비넌스 훅: `raw_data` 소스 표식, `source_kind`(자유 문자열 → `CARDSALES`/`HOMETAX`/`BANK_*` 등), `source_ref`, `fingerprint`, `external_account`.
- 신규가 필요한 것 (별도 collection 레이어, import 테이블 변경 없음): 외부 ID UNIQUE dedupe, raw payload 보존 테이블, 연결/실행 관리, 스케줄러, 자격증명 보관. → 07번 문서 참조.
