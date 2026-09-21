# 08 — 자동수집 구현 로드맵 제안

> PHASE 0 산출물. 순서는 "가장 작은 실제 데이터로 end-to-end 검증 가능" 기준 + 채택 게이트(07 §9) 의존으로 조정됨. 각 Phase는 별도 마이그레이션(`db/migration` V909+ 순차정책, docs/WORKFLOW.md §4) + docs/DOMAIN.md/API.md 동기 수정 수반.

## PHASE 0.5 — 벤더 사실 확정 (코드 0, 1~2주 병행 가능)
- 매통조 OpenAPI 이용기관 신청(자격·필드 스키마·쿼터·과금 확인) ← **카드매출 자동화의 go/no-go**
- CODEF 견적·데모 계정(YS 카드매출 현재 판매, KR_PB_NT_071 필드, 법인 계좌/카드 지원矩阵, 단가/SLA/법적근거 문서)
- 팝빌 홈택스수집·easyfinbank 견적 비교 (06 후속조사 결과 기반)
- 산출물: `AUTO_COLLECTION_SOURCE_MATRIX.csv` 확정판 + 게이트 판단 기록

## PHASE 1 — Collection foundation (가장 작게, 실제 데이터 e2e)
- 테이블 4종(sources/connections/runs/raw) + dedupe_key UNIQUE + raw 불변 저장
- **첫 어댑터는 Cardsales가 아니라 "ExcelFileAdapter(반자동)"**: 현재 이지샵 엑셀→csv_to_json 경로를 collection raw 보존 구조로 이식 — 외부 계약 없이도 raw→mapper→dedupe→import→confirm 전 경로 검증. (매통조 승인 전까지 메인 경로)
- CollectionRunner + lease/heartbeat 크론 + "지금 수집" 버튼(수동 트리거) + `/easy/connections` 최소 화면
-验收: 동일 파일 2회 수집 시 2회차 100% DUPLICATE_EXCLUDED, confirm 결과 불변(멱등성 증명)

## PHASE 2 — Cardsales (매통조 OpenAPI) — **PHASE 0.5 게이트 통과 시**
- 승인/매입/입금 3-API 어댑터 + 가맹점 전송동의 관리(동의ID·만료·재인증 UX)
- SALE/SETTLEMENT/PAYOUT 파생(mapper) + 소스 간 3중계상 금지 규칙 적용(07 §4)
- e2e: 소스 가맹점 1곳(학원 또는 퍼블리싱)의 최근 31일 실데이터 → 기존 batch 생성 → 수동 confirm
- 리스크: 이용기관 승인 실패·지연 → PHASE 1 폴백 유지

## PHASE 3 — Hometax (세금계산서/현금영수증 **조회**, 대사 목적)
- 벤더 채택(CODEF vs 팝빌) 후 어댑터 1개만 구현(연결 추상화 유지)
- connectedId 수명주기 관리(create/add/update/delete + CF-03002 재인증 흐름) — codef-io 공식 패턴 이식
- **우선 용도는 transaction 생성이 아니라 filing-check/evidence 보강**(filing_references 자동화) → 세무 리스크 없이 가치 선출하
- 재인증 필요 상태의 사용자 플로우(홈택스 2차인증 도입으로 정기 발생 전제)

## PHASE 4 — Bank
- 우선순위: ①개인사업자 계좌=오픈뱅킹 라이선스 벤더 경유(집중기관) ②법인=CODEF/팝빌 빠른조회 ③KB 기업고객이면 KB 기업OpenAPI 직접 평가
- 적요→거래처 Normalizer(별도 규칙 테이블) + bank_tran_id 안정성 retrospective 검증
- PAYOUT link 완성(카드 정산 입금 자동 대사)

## PHASE 5 — Business Card (사업용 카드 지출)
- CODEF 법인 승인/청구내역(9개 카드사) 어댑터. 지출 EXPENSE 행 자동 생성
- 재인증 주기(OTP) UX 재사용(PHASE 3 자산)

## PHASE 6 — Cross-source reconciliation 강화
- easy_collection_links + 매칭 알고리즘(07 §5), 카드 3단계+은행 입금 체인, 세금계산서↔거래 후보 REVIEW
- 미수금/수수료 계정 정책 세무 확정 → 필요 시 easy_accounts 확장(V번호 정책)
- high-confidence auto-confirm 정책 (별도 검토·설계 분리)

## PHASE 7 — Scheduler/monitoring 운영화
- 기관별 스케줄 파편화 회피(중앙 cron + 소스별 창 규칙), 실패 알림(n8n webhook 선례 활용), 수집 health 대시보드
- (선택) PG 어댑터: 토스페이먼츠 정산 API부터 — 이커머스 고객 발생 시

## 의존 관계 요약
```
P0.5(벤더) ──→ P2(Cardsales) ──→ P6(links)
P1(foundation) ─→ P2,P3,P4,P5 (P1 선행 필수)
P3(Hometax) ─→ P4 connectedId 재사용
P4(Bank) + P5(Card) ─→ P6
P6 ─→ P7
```
- 파일 폴백(P1 ExcelFileAdapter)은 어떤 게이트 실패에도 가치 있는 상시 안전망으로 유지.
- 각 Phase 완료 기준: unit(given/when/then) + 로컬 MySQL(`./scripts/run-local.sh`) e2e + 문서 동기(API.md/DOMAIN.md).
