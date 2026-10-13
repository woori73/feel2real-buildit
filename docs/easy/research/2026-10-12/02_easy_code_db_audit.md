# 기존 EASY 코드·DB 실사 보고서

기준: `woori73/feel2real-buildit` `main` 커밋 [`e4bec313`](https://github.com/woori73/feel2real-buildit/commit/e4bec313b2d533e2117e30eedf17a1595a80143d), 2026-10-12 10:10 KST  
방법: GitHub 코드·문서·Flyway·테스트 정적 실사. 코드 수정·DB 접속·운영 배포 없음.

## 1. 결론

EASY는 “14개 API”가 아니라 **EASY 도메인 Controller 14개, HTTP 매핑 96개** 규모다. 그 외 DTO 55, Entity/enum 59, Repository 35, Service 48, EASY 도메인 테스트 51개와 SSR 화면 5개가 있다. 초기 장부보다 훨씬 확장되어 다음 기반은 이미 존재한다.

- 사업체별 거래·학생·수납 분리
- 거래 수동입력/수정/삭제/CSV 내보내기
- 계정과목·외부코드 매핑·규칙 분류
- import staging, 일부 확정, 중복 의심, 원문 추적
- 차변/대변 검증, 분개 이력 void/repost, 시산표·총계정원장·손익·재무상태표
- 검토 큐, 수정 로그, 증빙/원천징수 검토 상태, 결산 잠금
- 수집 소스·연결·실행·raw record·checkpoint·lease
- 사업카드 수집 이벤트, 추천, 관리자 승인 후 전기

반면 다음은 구현 완료로 볼 수 없다.

- 사업자 회원별 tenant 소유권/권한
- 은행·카드·홈택스의 승인된 실운영 자동수집 전반
- 증빙 이미지 업로드/OCR/원본 보관
- 급여 계산·직원 원장
- 부가세·종소세·원천세 신고서 생성과 제출
- 세법 버전·공식 근거 저장·전자신고 포맷
- 비관리자용 EASY 제품 화면

## 2. 화면 및 보안 경계

코드상 화면은 다음 5개다. [`WebMvcConfig.java`](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/config/WebMvcConfig.java)

| URL | 템플릿 | 용도 |
|---|---|---|
| `/easy` | `easy/dashboard` | 월 요약·최근 거래·인사이트 |
| `/easy/transactions` | `easy/transactions` | 거래 CRUD·필터·import |
| `/easy/students` | `easy/students` | 학생 원장 |
| `/easy/payments` | `easy/payments` | 수납 관리 |
| `/easy/card-review` | `easy/card_review` | 사업카드 검토/승인 |

`/easy/**`와 `/api/admin/**`는 `ROLE_ADMIN`만 허용된다. 로컬 수집기 agent는 좁은 heartbeat/task 경로만 `ROLE_EASY_COLLECTOR_AGENT`에 열고 나머지 admin 경로 접근은 막는다. 근거: [`SecurityConfig.java` L207-L208, L432-L453](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/config/SecurityConfig.java#L207-L208).

**판정:** 사업체 분리는 FK와 `businessId` 필터 수준으로 구현됐지만 사용자-사업체 멤버십/소유권은 없다. 현재는 관리자가 `businessId=null`로 전체 사업체를 조회할 수 있는 내부 운영도구다. 독립 SaaS 전환 전 `user ↔ business ↔ role` tenant 경계가 필수다.

## 3. Controller/API 실사

| Controller | Base path | 매핑 수 | 핵심 기능 |
|---|---|---:|---|
| `EasyAccountController` | `/api/admin/easy/accounts` | 10 | 계정, 외부매핑, 분류규칙 |
| `EasyAccountingController` | `/api/admin/easy/accounting` | 8 | 원장, 시산표, 기초잔액, 개시승인, 재무제표 |
| `EasyAuditController` | `/api/admin/easy` | 16 | 검토, 수정미리보기, 원천징수, 결산 |
| `EasyBusinessController` | `/api/admin/easy/businesses` | 1 | 활성 사업체 목록 |
| `EasyCardReviewController` | `/api/admin/easy/card-events` | 7 | 카드 이벤트 검토/승인/가맹점 매핑 |
| `EasyCollectionController` | `/api/admin/easy/collections` | 10 | CSV ingest, 실행/소스/연결/checkpoint |
| `EasyCollectorAdminController` | `/api/admin/easy/collector` | 6 | 로컬 수집기·task 관리 |
| `EasyCollectorAgentController` | `/api/admin/easy/collector-agent` | 8 | heartbeat, task 상태 전이 |
| `EasyImportController` | `/api/admin/easy/imports` | 8 | staging, EasyShop import, prepare/confirm/reclassify |
| `EasyInsightController` | `/api/admin/easy` | 1 | 경영 요약 |
| `EasyLedgerController` | `/api/admin/easy` | 7 | 거래/요약/세무분류/CSV |
| `EasyReportController` | `/api/admin/easy` | 6 | 손익, 신고자료 대조, 소득 요약 |
| `EasyStudentController` | `/api/admin/easy/students` | 4 | 학생/학생별 원장 |
| `EasyTuitionController` | `/api/admin/easy/payments` | 4 | 수납 CRUD |

예: [`EasyLedgerController.java` L29-L93](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/controller/EasyLedgerController.java#L29-L93), [`EasyCollectionController.java` L34-L122](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/controller/EasyCollectionController.java#L34-L122).

## 4. DB 구조와 Flyway 계보

EASY 관련 마이그레이션은 19개다.

| 버전 | 핵심 스키마 |
|---|---|
| V973 | `easy_businesses`, students, transactions, tuition_payments |
| V974 | accounts, mappings, rules, import_batches/rows, 거래 확장 |
| V975 | review_items, withholding_records, annual_closings, provenance |
| V976 | journal_entries/lines, filing_references, correction_logs |
| V977~979 | reversal, expense/communication 계정·매핑 |
| V983 | collection_sources/connections/runs/raw_records |
| V986~987 | economic_sales/events/checkpoints, run lease |
| V988~990 | 카드수단·청구·이벤트·미지급·결제·할당, posted txn |
| V997~1000 | 로컬 수집기/task, 카드 확정일, 가맹점 매핑 |
| V1002~1006 | 실복식 기반 계정·은행/카드 맵·세무분류·cutover·감사컬럼 |
| V1014 | 은행 staging 증빙 repair |

핵심 DDL 근거: [`V973` L16-L100](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/resources/db/migration/V973__add_easy_ledger.sql#L16-L100), [`V974` L20-L150](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/resources/db/migration/V974__add_easy_import_foundation.sql#L20-L150), [`V976` L27-L117](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/resources/db/migration/V976__add_easyshop_pipeline.sql#L27-L117), [`V983` L14-L107](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/resources/db/migration/V983__add_easy_collection_foundation.sql#L14-L107), [`V1002` L22-L132](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/resources/db/migration/V1002__add_easy_real_double_entry_foundation.sql#L22-L132).

`easy_transactions.business_id`는 NOT NULL FK이며 Entity도 필수 연관이다. 근거: [`EasyTransaction.java` L37-L39](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/entity/EasyTransaction.java#L37-L39).

## 5. 복식부기와 분개

- 기존 `repost()`는 수입을 `Dr SETTLEMENT / Cr 수익`, 지출을 반대로 전기한다.
- 신규 `postLegs()`는 호출자가 복수 상대계정을 주며 차변합계=대변합계를 강제하고 신규 분개에서 기술계정 `SETTLEMENT` 사용을 금지한다.
- 거래 수정 시 기존 POSTED 분개를 VOID 처리하고 새 분개를 만들어 이력을 보존한다.
- 마감연도에는 전기를 차단한다.
- 실복식 서비스는 카드구매, 카드대금결제, 은행경비, 차입금 입금, 원금상환, 이자, 계좌이체를 명시적으로 분개한다.

근거: [`EasyJournalService.java` L25-L28, L66-L107, L130-L220](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyJournalService.java#L25-L28), [`EasyRealPostingService.java` L43-L161](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyRealPostingService.java#L43-L161).

**판정:** 복식 엔진은 재사용 가능(A)이나 모든 수동 거래가 아직 실질 상대계정 기반은 아니다. `SETTLEMENT` 기반 과도기 분개를 실제 자금계정으로 cutover하는 작업이 필요(B).

## 6. import·자동수집

### 구현 확인

- JSON row staging, EasyShop 전용 import, 최대 2,000행, prepare/부분 confirm/reclassify/discard.
- 원문 계정, raw JSON, source kind/ref, fingerprint 보존.
- 사업체·외부 ID·연결 ID 기반 dedupe key와 raw record→import row 추적.
- 수집 source/connection/run/checkpoint/lease와 visible local collector task 상태모델.
- 수집은 곧바로 장부에 쓰지 않고 staging→관리자 확정 경계를 거친다.

근거: [`EasyImportService.java` L170-L220, L278-L337, L524-L576](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyImportService.java#L170-L220), [`EasyCollectionIngestService.java` L265-L343](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyCollectionIngestService.java#L265-L343).

### 구현 완료로 볼 수 없는 부분

`CrefiaCardsalesAdapter`는 공식 endpoint와 회계 lifecycle 승인 전 실수집을 fail-closed하도록 명시적으로 예외를 발생시킨다. 따라서 CREFIA 클라이언트 코드가 있어도 운영 자동수집 완성으로 판단하면 안 된다. 근거: [`CrefiaCardsalesAdapter.java` L75-L91](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/CrefiaCardsalesAdapter.java#L75-L91).

## 7. 분류 추천·검토·승인

카드 분류 추천은 `관리자 명시 매핑 → 동일 가맹점 과거 이력 → 정규화 가맹점 매핑 → 규칙` 순서이고 HIGH/MEDIUM/LOW 신뢰도를 반환한다. 자동 전기하지 않으며 관리자가 승인한 PURCHASE만 거래와 분개를 만든다. 취소·정정·결제는 아직 관찰 전용이다.

근거: [`EasyCardClassificationRecommendationService.java` L13-L22, L74-L176](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyCardClassificationRecommendationService.java#L13-L22), [`EasyBusinessCardAccountingService.java` L228-L276](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyBusinessCardAccountingService.java#L228-L276).

검토 큐는 미분류, 증빙, 사업체, 원천징수, 중복 의심, 미확정 import, 누락 경비 7종을 재빌드한다. 해결 시 거래 수정→재분개→수정로그를 남긴다. 근거: [`EasyAuditService.java` L83-L130, L286-L385](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyAuditService.java#L83-L130).

## 8. 보고서·세금 기능의 실제 수준

- 연간/월별 손익, 전년 대비, ledger↔journal 일치, 미전기 건수를 계산한다.
- 총계정원장·시산표·손익계산서·재무상태표가 있다.
- 신고자료는 사용자가 입력한 `filing_reference`와 장부 수입·비용·소득을 비교해 MATCHED/NEEDS_REVIEW를 만든다.
- 차이를 역산하지 않고 세법 판단·세율·공제를 계산하지 않는다고 코드가 명시한다.

근거: [`EasyReportService.java` L55-L119, L294-L426](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyReportService.java#L55-L119), [`EasyFinancialStatementService.java` L23-L118](https://github.com/woori73/feel2real-buildit/blob/e4bec313b2d533e2117e30eedf17a1595a80143d/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/service/EasyFinancialStatementService.java#L23-L118).

**판정:** 장부·재무보고는 구현(A/B), 신고자료 대사는 기초 구현(B), 부가세/종소세/원천세 신고서 생성·전자파일·제출은 신규(C/D).

## 9. 재사용 우선순위

1. `easy_businesses`와 모든 business FK를 tenant 모델의 출발점으로 사용하되 멤버십을 추가한다.
2. import/collection raw→staging→confirm 파이프라인을 모든 외부 수집원의 공통 seam으로 유지한다.
3. `postLegs`, 마감 잠금, void/repost, correction log를 원장 핵심으로 승격한다.
4. 카드 추천 우선순위와 관리자 승인 경계를 AI suggestion 공통 인터페이스로 일반화한다.
5. filing reference/check는 신고 사전검증 엔진의 비교 레이어로 재사용한다.

## 10. 주요 리스크

- 관리자 전용 구조를 그대로 외부에 열면 사업체 간 데이터 노출 위험이 있다.
- 일부 조회 API에서 `businessId=null`은 전체를 뜻하므로 tenant 서비스에서는 금지해야 한다.
- 공급자 adapter 존재를 실운영 수집 완료로 오인하면 안 된다.
- 신고 기능 명칭이 있어도 실제 신고서·세율 엔진은 없다.
- 운영 DB 실사·실제 API 응답 검증은 이번 제한에 따라 수행하지 않았다.


