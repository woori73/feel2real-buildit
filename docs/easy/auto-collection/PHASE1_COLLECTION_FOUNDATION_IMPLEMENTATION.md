# EASY 자동수집 PHASE 1 — Collection Foundation + 파일 어댑터 구현

> 상태: **구현 완료(빌드/단위·멱등 테스트 통과), PR 대기** · 작성 2026-09-20 · 브랜치 `feat/easy-collection-foundation-20260920`
> 선행 문서: `01_ARCHITECTURE.md`(설계) — 이 문서는 "현재 main 코드와 대조한 실제 seam + 구현된 것 + 검증된 것"을 기록한다.

## 1. 목표와 불변 원칙

기존 EASY 회계 파이프라인(수집 → staging → 검토/확인 → 확정 → 분개)의 **앞단**에 공통 수집 기반을 추가한다.

- **자동 collect ≠ 자동 confirm.** 이 PHASE에서 장부(transaction) 생성 경로는 기존
  `EasyImportService.confirm()`(관리자 확정) 하나뿐이다. 자동화 코드는 confirm을 호출하지 않는다.
- **`easy_transactions` 직접 INSERT 금지.** 인계는 전적으로 `POST /api/admin/easy/imports`와
  동일한 `EasyImportService.stage()` 계약을 통과한다.
- 기존 import 계약·account mapping·분류 규칙·READY/REVIEW·선택적 확정·fingerprint·journal·ledger·summary·filing-check **무변경** (diff로 증명).

## 2. PHASE 0 문서를 main과 대조해서 확인한 사실

- import seam은 `EasyImportController.stage(EasyImportBatchRequest)` — `EasyImportBatchRequest(businessId, sourceName, sourceKind, rows)`이고,
  `EasyImportRowInput`은 `occurredOn:String` 등 12필드(canonical) + V974/V976 호환 생성자. **실제 계약과 일치**하므로 그대로 사용.
- `EasyImportRowResponse`에 `rowId`가 노출되어 있어 **raw ↔ staging row 역링크가 API 수준에서 가능** (PHASE 0 가정 확인됨).
- `EasyFingerprint.of(businessId, occurredOn, direction, amount, counterparty, description, payMethod)` — businessId 포함, SHA-256, 비unique(의심 탐지용). 3단계 dedupe에서 "이미 확정된 장부 거래와 동일한 행" 배제에만 사용(자동 합치기 없음).
- POI/excel 의존성이 build.gradle에 **없음** → PHASE 0 설계대로 XLSX 파싱은 보류하고 CSV 어댑터로 시작.
- 마이그레이션 최신 번호는 V982 → **V983** 사용.

## 3. 스키마 (V983, 4개 테이블 — additive만)

`src/main/resources/db/migration/V983__add_easy_collection_foundation.sql` / 상세 컬럼: `docs/DOMAIN.md` §4.55.

| 테이블 | 역할 | 핵심 제약 |
|---|---|---|
| `easy_collection_sources` | 수집원 카탈로그 | `code` UNIQUE, active 시드는 `EXCEL_FILE`만 |
| `easy_collection_connections` | business↔source 연결 | (business, source, label) UNIQUE, `connection_ref`는 참조자만 |
| `easy_collection_runs` | 실행 provenance | 상태 RUNNING/SUCCESS/PARTIAL/FAILED + 4개 카운터 + `import_batch_id` |
| `easy_collection_raw_records` | 원문 불변 보존 | **`dedupe_key` UNIQUE**, raw_payload 수정 금지(상태 전이만) |

destructive 없음: 기존 테이블·컬럼 변경 0건.

## 4. 아키텍처 / 데이터 흐름

```
CSV 파일(UTF-8|CP949)
  └─ POST /api/admin/easy/collections/ingest/csv (multipart businessId+file[+label])   ROLE_ADMIN
      └─ EasyCollectionIngestService.ingestCsv()                        [1 트랜잭션]
          ├─ connection findOrCreate(EXCEL_FILE, business, label)
          ├─ run(RUNNING) 저장
          ├─ EasyCsvFileAdapter.parse() → CollectionParsedRow[]          (한/영 헤더 별칭, 날짜/금액/구분 정규화)
          ├─ 행별 dedupe:
          │    ① dedupeKey = EXT키(승인번호 있으면) 또는 T키(튜플해시16+파일 내 등장 순번)
          │    ② run 내 seen-set + rawRepository.findByDedupeKey → 히트면 duplicate (그만)
          │    ③ 저장 후 fingerprint가 기존 easy_transactions와 충돌 → raw=LEDGER_DUPLICATE_EXCLUDED, 인계 안 함
          ├─ 정규화 실패 행 → raw=ERROR(원문 보존), 카운터 error
          ├─ accepted행 → EasyImportBatchRequest(sourceName="파일명#run:{runId}", sourceKind="FILE_CSV",
          │      rows=[EasyImportRowInput(..., rawData=원문JSON+" [ext:{dedupeKey}]", externalAccount=계정명)])
          │      → EasyImportService.stage()  ← 기존 경계 그대로 (매핑→READY/분류 제안/2000행 제한 포함)
          ├─ stage 응답 rowNo↔raw 매핑으로 raw.linkImport(batchId,rowId) → IMPORT_LINKED
          └─ run.finish(카운터, importBatchId) — SUCCESS/PARTIAL/FAILED, connection.last_success_at 갱신
```

- run 전체가 1트랜잭션이라 **raw 저장과 staging 인계가 원자적**이다: 중간 실패 시 롤백되어
  "raw에만 있고 import에 없는" 크래시 창이 DB에 남지 않는다(재시도하면 처음부터 다시 수행).
- raw의 dedupe_key UNIQUE가 재수집 멱등의 최종 방어(두 번 save 시도 자체가 충돌).

## 5. dedupe 설계와 TEST A~F

정규화: NFKC(전각/반각) + trim + 연속 공백 축소 + 소문자 (`EasyCollectionDedupeKeys.normalizeText`).

- **EXT 키** `{SOURCE}:{biz}:EXT:{normalize(외부ID)}` — provider 고유 ID가 있으면 내용이 달라도 중복.
- **T 키** `{SOURCE}:{biz}:T:{tupleHash[0:16]}:{seq}` — seq는 "이 파일에서 이 튜플이 몇 번째 등장인지".
  파일명·행 순서와 무관하게 동일 내용 재업로드면 키가 동일하고, 우연히 튜플이 같은 서로 다른 실제 거래는
  seq가 달라 나란히 살아남는다(과도한 dedupe 금지).

| Test | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| A | 신규 3행 최초 수집 | accepted 3, 배치 생성, raw 전부 IMPORT_LINKED, txn 0건(confirm 없음) | ✅ |
| B | 동일 파일 재업로드(파일명 다름) | accepted 0, duplicate 3, 새 배치 없음 | ✅ |
| C | 승인번호 동일 + 내용 변경 | EXT 키 우선 → duplicate | ✅ |
| D | 행 순서 변경 재업로드 | 튜플 키 불변 → 전건 duplicate | ✅ |
| E | 1행 추가 후 재업로드 | 신규 1행만 accepted | ✅ |
| F | 동일 튜플 2건(다른 실제 거래) | seq 1,2로 나란히 accepted / 재업로드 시 둘 다 duplicate | ✅ |
| + | 기존 장부 fingerprint 충돌 | raw LEDGER_DUPLICATE_EXCLUDED, 인계 0 | ✅ |
| + | 파싱 실행 | raw ERROR 보존 + run PARTIAL | ✅ |
| + | .xlsx | run FAILED(명시 거부), import 없음 | ✅ |

테스트 파일: `src/test/java/.../easy/service/EasyCollectionIngestIdempotencyTest.java`
(EasyShopPipelineHarness의 Map 리포지토리 + **실제** EasyImportService 재사용 — 가짜 import가 아님.
dedupe_key 충돌 시 IllegalState로 실패하도록 UNIQUE를 모사.)

## 6. 파일 어댑터 계약 (EasyCsvFileAdapter)

- ADAPTER_CODE `FILE_CSV` v1. 입력: 바이트 + 파일명. 출력: `CollectionParsedRow`(원문 맵 + rawJson + 정규화 필드 + 행 단위 error).
- 헤더 별칭(한/영, 대소문자·공백 무관): 거래일/거래일자/date, 구분/입출구분/direction, 금액/amount,
  거래처/상대방/counterparty, 적요/메모/description, 결제수단/payMethod, 공급가액, 부가세/세액, 증빙, 계좌명/원본계정(account), 승인번호/거래번호/REFNO(externalId).
- 필수 헤더: 거래일, 금액. 값 해석: 날짜 4포맷(yyyy-MM-dd|yyyy.MM.dd|yyyy/MM/dd|yyyyMMdd),
  금액(콤마/₩/원/음수 표시, 소수점 미지원→행 오류), 구분(수입·입금·수금→INCOME / 지출·출금·지급→EXPENSE).
- 디코드: UTF-8(BOM 허용, strict) 실패 시 CP949. CSV는 최소 RFC4180(따옴표/쉼표/개행/"" 이스케이프).
- **특정 은행/카드사 포맷 변환은 이 어댑터가 아니라 향후 전용 어댑터의 책임** — 공통 계약(수집원 카탈로그 + 어댑터 코드/버전 + raw/정규화 분리)이 확장점이다.

## 7. 보안

- 저장 대상 아님(구조적으로 컬럼 없음): 금융기관 ID/PW, 공동인증서(+비번), OTP, API secret, access/refresh token.
  `connection_ref`는 provider connectedId/동의ID류 **참조자**만(대법 2020도9972 주석 명기).
- fixture는 전부 synthetic(학부형A 등). push 전 secret scan 수행.
- API는 `/api/admin/**` 패턴이라 SecurityConfig에 의해 ROLE_ADMIN 강제(추가 설정 없음).

## 8. 알려진 제한 (PHASE 1 의도적 범위)

- XLSX 미지원(POI 의존성 없음) → CSV로 저장해 업로드. `.xlsx`는 조용한 실패 대신 run FAILED.
- 소수점 금액 미지원(장부 금액형이 Long) → 해당 행은 ERROR로 보존 후 원문 확인.
- T 키의 seq는 "파일 내 등장 순번"이라, **이미 수집된 행들 사이에 동일 튜플 행을 중간에 삽입**하면
  이후 seq가 밀려 그 튜플의 꼬리 등장분이 신규로 인정될 수 있다(같은 튜플 다건 거래가 빈번한 소스에서는
  externalId 컬럼을 채우는 것을 권장). 데이터 손실 없이 중복-수락 방향이라 검증 가능한 실패다.
- cardsales/CODEF/팝빌/홈택스/은행/PG 실제 API 연결 없음(연결 테이블·카탈로그만 준비).
- 관리자 화면은 최소 API(run 목록/단건/source 목록)만 제공 — UI는 확인 필요 시 별도 작업.

## 9. 검증

- `./gradlew compileJava` / `test --tests "...domain.easy.*"` 통과(easy 도메인 전체 회귀 포함).
- 전체 `build`의 contextLoads/PageTemplateRender 계열 실패는 로컬 MySQL(3308) 미기동 환경 문제
  (Connection refused)이며 이 PR 코드와 무관 — CI(MySQL service)에서 최종 확인.

## 10. 다음 단계 (PHASE 2 후보)

1. 실제 카드매출(cardsales OpenAPI) 어댑터 — connection_ref/동의 흐름과 어댑터 등록이 이 기반의 1차 시험대.
2. run 단위 재시도(retry)와 부분 재수집 커서(기간 슬라이스).
3. raw/정규화 원문의 관리자 조회 UI + 검증 리포트.
4. 자동 collect → **사람 confirm** 유지 하에서의 배칭 최적화(2000행 분할 전략).

## 11. 관련

- PHASE 0: `docs/easy/auto-collection/01_ARCHITECTURE.md` 외 8종 (`research/easy-auto-collection-20260920`에서 cherry-pick: 0e6f7e2)
- 코드: `domain/easy/{controller/EasyCollectionController, service/EasyCollection*, service/EasyCsvFileAdapter, entity/EasyCollection*, repository/EasyCollection*, dto/EasyCollection*, dto/CollectionParsedRow}` + `V983__add_easy_collection_foundation.sql`
- 계약 문서: `docs/API.md`(자동수집 foundation 섹션), `docs/DOMAIN.md` §4.55
