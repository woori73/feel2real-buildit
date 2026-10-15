# 07 — 자동수집 아키텍처 설계 (목표안, 미구현)

> 근거: 01(현재 코드) + 02~06(조사). 원칙: **기존 Import→mapping→READY/REVIEW→confirm→journal 파이프라인 불변**, 자동수집은 그 앞단에 별도 collection 레이어로 부착. PRODUCTION WRITE/MIGRATION 없음 — 본 문서는 설계만.

## 1. 전체 구조

```
[SOURCE ADAPTERS]                [COLLECTION LAYER (신규)]              [기존 EASY (변경 없음)]
CardsalesAdapter (매통조 OpenAPI)   easy_collection_sources                POST /api/admin/easy/imports
HometaxAdapter   (CODEF/팝빌 중계)  easy_collection_connections            → staging (batches/rows)
BankAdapter      (오픈뱅킹/집중기관) easy_collection_runs        → normalize → /easyshop JSON 계약
BusinessCardAdapter(CODEF 법인)     easy_collection_raw         → dedupe      → account mapping
PgAdapter        (후순위)           easy_collection_links       → classification → READY/REVIEW
        ↑ Scheduler(철야 cron)+수동 "지금 수집"        ↓                  → confirm → txn/journal/ledger
   파일 업로드 반자동(엑셀 폴백)은 기존 경로 그대로           ↓
                                                    CollectionDedupService → CONFIRM 대상 자동 생성(정책 전환점, §5)
```

- 어댑터는 **수집+raw 보존까지만** 담당하고, 회계 데이터 생성은 기존 파이프라인에 위임. raw→import payload 변환은 어댑터별 Mapper(순수 함수)로 분리.
- 머신 실행 경로 인증: `ChartAgentTokenFilter` 선례 패턴의 narrow token (Admin 세션 없이 워커만 호출 허용, 화면 API는 ROLE_ADMIN 유지).

## 2. 어댑터별 소스 계약 (02~06 조사 반영)

| 어댑터 | 채널 | 인증/연결 | 수집 단위 | dedupe 1차 키 |
|---|---|---|---|---|
| Cardsales | **매통조 OpenAPI** (openapi.crefia.or.kr, 승인/매입/입금/가맹점 4종) | 자사 client_id/secret + **가맹점별 전송이용 동의** | 승인번호별, 매입별, 입금(일x카드사)별 | 승인: `CARDSALES:{가맹점}:{승인번호}` / 매입: `{가맹점}:{매입일자}:{매입금액}` / 입금: `{결제계좌}:{입금일자}:{실입금액}` |
| Hometax | CODEF `taxinvoice-integrated-list` 또는 팝빌 홈택스수집(웹훅 1h) — **채택 전 §9 확정 조건** | 연결: 사용자의 공동/금융인증서·ID+2차인증(벤더 보관, EASY는 connectedId만) | 세금계산서 1건 = issued/saved + 공급자번호 + 작성일자 | `HOMETAX:{공급자사업자번호:{공급자/수취자}:{발급번호}` (없으면 날짜+공급자+수취자+공급가액+세액) |
| Bank | 오픈뱅킹(개인사업자) / CODEF-팝빌 법인 / 파일 폴백 | OAuth consent(오픈뱅킹) 또는 connectedId | 건별 거래 | `BANK:{기관}:{계좌}:{bank_tran_id}` 없으면 `{계좌}:{일시}:{입출구분}:{금액}:{잔액}` |
| BusinessCard | CODEF 법인 승인/매입/청구내역 | connectedId(B) + 재인증 주기 | 승인 1건 | `{카드사org}:{승인번호}` 없으면 `{카드사}:{사용일시}:{가맹점}:{금액}` |
| PG | 각사 공식 API(토스 정산 API 등) / PortOne 단일배대 검토 | siteId/apiKey(자사 시크릿) | 결제·취소·정산·입금 | `{PG}:{paymentKey 또는 주문번호}` / 정산: `{PG}:{정산일}:{사이트}` |

- **거래처/적요 파싱**: 어떤 은행 경로도 구조화 상대방 필드를 주지 않음(04 §A1) → `counterparty`는 별도 Normalizer(적요 정규식+매핑 테이블) 담당, 기존 classification_rules와 별도 레이어로 유지(회계 매핑 규칙과 파싱 규칙의 수명주기가 다르다).

## 3. Collection 레이어 데이터 모델 (개요; 실제 migration은 PHASE 1에 `db/migration` V909+/번호정책 준수)

- **easy_collection_sources** — 소스 카탈로그(기관 코드, 어댑터 타입, 배지 필드 정의, 활성).
- **easy_collection_connections** — business_id ↔ 소스 연결. 상태(`CONNECTED/NEEDS_REAUTH/DISABLED`), **connectedId/토큰 참조자(암호화 컬럼 또는 시크릿 store 포인터, 원본 자격증명 절대 저장 금지)**, 마지막 성공시각, 재동의 만료일(매통조 동의·홈택스 2차인증 주기).
- **easy_collection_runs** — 실행 로그. (connection, 기간, 트리거=AUTO/MANUAL, 상태 RUNNING/SUCCESS/PARTIAL/FAILED, 수집/중복/실패 카운트, 오류 요약, next_retry_at).
- **easy_collection_raw** — **원본 보존(1차 아티팩트)**: `source`, `external_account_id`, `external_transaction_id`, `collected_at`, `source_occurred_at`, `raw_payload(JSON)`, `raw_hash(SHA-256)`, `status(COLLECTED/PROCESSED/DUPLICATE_EXCLUDED/DEFERRED/FAILED)`, `retry_count`, `dedupe_key UNIQUE`, mapped import_batch_id/row_id(생성 시).
  - 불변성: raw는 수정·삭제 금지(감사·재처리 근거). 재생성은 raw→mapper 재실행으로 가능.
  - hash는 동일 payload 재집행 무해(idempotent reprocess) 보장용.
- **easy_collection_links** — 크로스소스 관계(§5).

## 4. Dedupe 설계 (3중 방어)

1. **1차 — external key UNIQUE**: `easy_collection_raw.dedupe_key`(§2 표)가 실제|unique 제약. 재수집·중복 크론에서 물리 차단.
2. **2차 — composite 폴백(외부 ID 없을 때)**: `(source, account, normalized_date, amount, direction, normalized_merchant/counterparty, 승인번호있으면 포함)`의 정규화 해시. jangbu의 약점(자유텍스트 노이즈) 보완: 전자기호/공백/전각-반각 정규화 후 계산, **금액+날짜는 절대 제외하지 않되 텍스트는 fuzzy정규화**.
3. **3차 — 기존 fingerprint 시스템**: confirm 시점의 `EasyFingerprint`/`DUPLICATE_SUSPECT` 리뷰는 그대로 최종 안전망(자동합치기 금지 정책 유지). **서로 다른 소스의 동일 경제적 거래는 fingerprint가 달라도 정상** — §5 관계 테이블로 판정하고 import 행 생성 단계에서 소스 간 충돌을 조정.

- **소스 간 동일 거래 3중 계상 금지(핵심 규칙)**: 카드매출은 Cardsales(승인)과 은행(입금)과 홈택스(발행수신세액)에 모두 나타남. 규칙: **SALE은 1개 소스만 생성** — 우선순위 Cardsales 승인 > 카드사/홈택스 집계 대체 > 미커버 시 엑셀. 은행 입금은 SALE이 아니라 **PAYOUT(정산 수령)** 로만 생성(§5 link가 있으면). 홈택스 자료는 transaction 생성 없이 **대사/evidence** 용도 우선. 이 판정은 `CollectionLinkPolicy`(§5)가 담당.

## 5. Cross-source matching (카드매출 수명주기 추적)

`카드승인 100,000 → 카드사 매입(수수료 2,000 차감) → 지급예정 98,000/지급예정일 → 은행 실입금 98,000`

- `easy_collection_links`: `(raw_id_A, raw_id_B, link_type, confidence)` — link_type ∈ {APPROVAL_TO_PURCHASE, PURCHASE_TO_PAYOUT, PAYOUT_TO_BANK_DEPOSIT, TXN_TO_BANK_DEPOSIT, TAX_INVOICE_TO_TXN}
- 매칭 알고리즘(확정적+휴리스틱 2단계): ① 입금정보 API는 "같은 row에 매출금액/실입금액/보류금액"을 주므로 **동일 raw 내에서 SALE/FEE/PAYOUT 수치 파생**(교차소스 매칭 불요) — 매통조 우선 채택의 결정적 이유 ② 은행 실입금 link는 `(결제계좌, 입금일자, 실입금액)` 정확 매칭 → 없으면 ±N일 금액 부분매칭(합산 정산 케이스) REVIEW행 ③ 세금계산서↔거래는 (사업자번호+금액+날짜창) 후보 생성 후 REVIEW.
- 회계 표현: 1 건의 승인 = 매출(INCOME 100,000), 정산 시 (차)은행 98,000 (차)카드수수료 2,000 (대)카드미수금/매출 100,000 — **fee를 별도 EXPENSE 트랜잭션으로 만들지 않고 정산 1건에 내장**하는 것을 기본 정책으로 하되, 미수금 계정 정책은 PHASE 6에서 세무 검토 후 확정(현재 easy_accounts에 SETTLEMENT만 존재 — 계정 추가 필요 가능, V909+ 범위로).
- `status` 흐름: raw CONFIRMED_LINKED 시에만 PAYOUT 행이 import로 전달.

## 6. 수명주기: raw → 기존 import

```
raw(COLLECTED) → mapper → dedupe 통과 → import batch 자동 생성
  source_name = "{SOURCE}:{connectionId}:{yyyy-MM-dd}..{dd}"  (현행 규칙과 동일 형태)
  source_kind = CARDSALES / HOMETAX / BANK_{org} / CARD_{org} / PG_{name}
  external_account = "{SOURCE}:{원문 계정/가맹점명}"   ← 원문 보존 원칙 그대로
  raw_data = 원본 JSON + [ext:{external_transaction_id}] 표식
→ 이후는 전량 기존 흐름(mapping→classify→DUPLICATE→READY/REVIEW→confirm→journal)
```
- 자동 collect ≠ 자동 confirm: **확인(confirm)은 사람 유지가 기본**(REVIEW 철학 일관). PHASE 6+에서 high-confidence(영향금 1.0 매핑+dedupe 통과+link 완결) row의 auto-confirm은 정책 플래그로 분리.

## 7. 보안 모델 (03 §0, 04 §1.3 법적 제약 반영)

| 대상 | 저장 위치 | 평문 금지 |
|---|---|---|
| 자사 운영 시크릿 (매통조 client_secret, CODEF client_id/secret, 팝빌 key, PG apiKey) | **`/etc/f2r/env` systemd EnvironmentFile (현행 패턴) 또는 AWS Secrets Manager/SSM으로 승격**. 코드·DB·Git에 절대 부호화 전까지 0건 | ✅ |
| 고객 연결 자격증명 | **EASY는 보유하지 않거나, 보유 불가 항목으로 분류** — connectedId(토큰 참조자)만 DB 저장. 매통조 **가맹점 전송동의**는 협회 기준 레코드(동의ID·만료)만 보관 | ID/PW·공동인증서 파일·OTP 절대 저장 금지(대법 2020도9972) |
| connectedId | DB 컬럼 저장 OK(그 자체로 자격증명 아님), 단 접근은 ADMIN 감사 로그 대상 | — |
| access_token (CODEF 주간 토큰 등) | 인메모리 캐시 우선, 디스크 캐시는 chmod600+만료 | — |
| raw_payload(PII: 계좌번호·사업자번호·승인번호) | DB 저장 필요. 컬럼 단위 마스킹 뷰(jangbu masking 정규식 참고), 로그 출력 금지 | — |
- GitHub secret 0 유지: `.env*` 커밋 금지 + pre-commit secret scan(sx-secrets-detect) 유지.
- 신규 시크릿은 도입 시점에 현행 env 패턴 유지 vs AWS Secrets Manager 전환을 PHASE 1에서 결정(전환 시 EC2 IAM 정책 추가, 문서화: AWS_DEPLOYMENT.md).

## 8. Scheduler (설계만)

- Spring `@Scheduled`, cron env-오버라이드(`ChartSiteToolService:222` 패턴): `easy.collection.cron` 기본 철야(예 02:30 KST). 일별/기관별 백로그(전전년도~31일 창 관점) 커버.
- 다중 인스턴스: `AlbAccessStatisticsWorker`의 lease/heartbeat 패턴으로 run 단위 락(1 connection = 1 active run).
- 재시도: connection 단위 exponential backoff(예: 5분→4회), 벤더 401/CF-03002/Throttled 분기(토큰 재발급 후 1회 재시도 = codef-io 공식 패턴). **NEEDS_REAUTH 감지 시 재시도 중단 + 사용자 알림 상태**.
- 수동 트리거: `/easy/connections`의 "지금 수집" → 동일 run 파이프라인.
- 운영 지표: 마지막 성공시각, 최근 실패 사유, pending reauth 수 (→08 문서 모니터링 Phase).

## 9. 채택 게이트 (구현 착수 전 사실 확정 필요 항목)

1. **매통조 이용기관 신청** — 자격/필드 스키마/쿼터/과금 (없으면 CardsalesAdapter는 엑셀 반자동으로 강등)
2. **CODEF** — YS 가맹점카드매출 현재 판매 여부, KR_PB_NT_071 디테일, 단가·레이트리밋·SLA, 자동조회대행 신고 근거 문서
3. **팝빌** — 홈택스수집·빠른조회 가격/계약, 부서사용자 권한 범위 (후속 조사 반영)
4. **오픈뱅킹 참가 vs 집중기관** — 전자금융업 인가 없이 갈 수 있는 범위 판단(개인사업자 계좌는 마이데이터 라이선스 벤더 경유가 유력)

## 10. 사용자 화면 초안 (`/easy/connections`, ROLE_ADMIN)

```
[여신금융협회(매통조)]  연결됨 · 가맹점 3 · 동의 만료 2026-12-31 · 마지막 수집 09-20 02:30 ✓ [지금 수집]
[홈택스(CODEF)]        재인증 필요 ⚠ (2차인증 만료) · 마지막 수집 09-18 [재인증] [지금 수집]
[국민은행]             연결됨 · 계좌 2 · 마지막 수집 ✓
[신한카드(법인)]        연결됨
──────────────────────────────
최근 자동수집: run별 수집/중복제외/실패 카운트, REVIEW 필요 미리보기, 중복 제외(소스 간 충돌) 목록
```
- 상태는 run/connection 테이블에서 집계, 신규 화면이지만 기존 easy SSR(Thymeleaf) 패턴 유지.

## 11. 기존 구조에 대한 변경 없음 보장
- easy_import_* 스키마·confirm·journal 코드 수정 0건으로 설계 성립(import rows.raw_data/external_account/source_kind가 이미 자유 문자열 수용).
- Batch 5·운영 데이터 무접촉. collection 테이블은 append-only 성격 + review만.
