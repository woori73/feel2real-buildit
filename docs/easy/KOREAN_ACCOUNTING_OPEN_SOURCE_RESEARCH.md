# 한국 회계·장부 오픈소스 리서치 (2026-09-16)

> 목적: 해외 프로그램의 한국식 변환이 아니라, 처음부터 대한민국 사업자 환경을 고려한
> 구현·데이터 모델을 찾는다. 코드는 가져오지 않고 조사만 했다.
> 원칙: 라이선스 없음 = 사용 불가. AGPL/GPL 코드 복사 금지.
> 세법 주의: "구현되어 있다" ≠ "현행 세법상 정확하다". 세율·서식은 별도 검증이 필요하다.

## 검색 키워드 (한/영)

한국 회계, 회계장부, 복식부기, 간편장부, 사업자 장부, 회계 프로그램, 세무 프로그램,
세금계산서, 부가가치세, 종합소득세, 계정과목, 원천징수, 가계부, 홈택스,
Korean accounting, Hometax, double entry ledger

## 후보 총평 (14개 조사)

| # | 프로젝트 | stars | 상태 | 라이선스 | 등급 |
|---|---|---|---|---|---|
| 1 | [rhl0509/erp](https://github.com/rhl0509/erp) | 0 | 실제 구현, 2026-08 활동 | 없음 | C |
| 2 | [aivance-kr/AICassa](https://github.com/aivance-kr/AICassa) | 1 | 실제 구현, 2026-09 활동 | MIT | A |
| 3 | [Central-MakeUs/Easybud-Server](https://github.com/Central-MakeUs/Easybud-Server) | 8 | 실제 구현, 2024-02 이후 정체 | 없음 | C |
| 4 | [sebae1/SBS](https://github.com/sebae1/SBS) | 0 | 실제 구현(데스크톱), 2025-02 | Apache-2.0 | A |
| 5 | [tyohnn/holiday-cfo](https://github.com/tyohnn/holiday-cfo) | 0 | 실제 구현, 2026-08 활동 | MIT | B |
| 6 | [bella-portfolio/double-entry-ledger](https://github.com/bella-portfolio/double-entry-ledger) | 0 | 실제 구현(미니), 2026-05 | 없음 | C |
| 7 | [HanTJ/budget-book](https://github.com/HanTJ/budget-book) | 0 | 실제 구현, 2026-06 활동 | 없음 | C |
| 8 | [junpakPark/spring-cashbook](https://github.com/junpakPark/spring-cashbook) | 0 | 실제 구현(미니), 2026-07 | 없음 | C |
| 9 | [sonjong980304-tech/journal-entry-test-agent](https://github.com/sonjong980304-tech/journal-entry-test-agent) | 1 | 실제 구현(검증 에이전트), 2026-08 | 없음 | C |
| 10 | [shoo99/jangbu](https://github.com/shoo99/jangbu) | 0 | 빌드 산출물만 (소스 없음) | 없음 | 제외 |
| 11 | [lbiz-partners/hometax-doum](https://github.com/lbiz-partners/hometax-doum) | 51 | 실제 운영(스킬), 2026-09 활동 | Other(재판매 금지) | C |
| 12 | [jaehyuk-choi-KICPA/hometax-vat-filing-assist](https://github.com/jaehyuk-choi-KICPA/hometax-vat-filing-assist) | 0 | PoC, 2026-07 | 없음 | C |
| 13 | [junhyeokkk/wonjang](https://github.com/junhyeokkk/wonjang) | 0 | 스켈레톤 (원장 코드 없음) | 없음 | 제외 |
| 14 | [Orphy-a/Odoo-Accounting](https://github.com/Orphy-a/Odoo-Accounting) | 1 | Odoo 통째 복사 (.pyc 포함) | 없음 | 제외 |

등급: A=코드 재사용 가능 / B=제한적 가능 / C=복사 금지·구조/아이디어만 참고.
가계부(개인 자산관리)는 사업자 회계 후보에서 분리 — boostcamp 계열, Uliga 등은 본 문서 범위 밖.

---

## 1. rhl0509/erp — 가장 완성된 한국형 복식부기 설계 (C)

- URL: https://github.com/rhl0509/erp · Python(FastAPI+SQLAlchemy+MySQL) + Next.js · 171 파일
- 라이선스 없음 → 코드 복사 금지. 설계 문서·구조만 참고.
- 실제 구현: `journal_entries` / `journal_lines` / `accounts` 전기형 GL.
  - 불변식: 전표별 Σ차변=Σ대변 (서비스 assert), 0원 라인 금지, `(source_type, source_id)` UNIQUE 멱등.
  - 산출물: 분개장·계정별원장·시산표·손익계산서·재무상태표 + 재대사(reconcile) + 전체 재전기(rebuild).
  - 한국형: 4자리 계정코드 (1110 현금및예금, 1120 외상매출금, 1130 상품, **1140 부가세대급금**, 2110 외상매입금, **2120 부가세예수금** …), 면세는 세액 라인 생략, 세금계산서는 전기 제외(신고 보조).
  - 마감: 월 마감선 + CLOSING 대체분개(3110 이월이익잉여금), Asia/Seoul 기준일자 처리.
- 참고 가치: **최고 (복식부기 전체 구조)**. 특히 파생형/전기형 비교 결론(전기형+재생가능),
  원천-전표 1:1 멱등, 재대사 리포트는 /easy 복식부기 확장 시 그대로 쓸 설계다.

## 2. aivance-kr/AICassa — 국세청 간편장부 + 부가세 자동계산 (A)

- URL: https://github.com/aivance-kr/AICassa · PHP(CodeIgniter4) · MIT → 재사용 가능.
- 실제 구현 (간이사업자 ERP): `ledger_entries` 단식 + VAT 분리 저장.
  - 한국형 계정 시드: **국세청 간편장부 계정 체계** (수입: 매출/기타, 비용: 상품매입·급료·제세공과금·임차료·지급이자·기업업무추진비·기부금·감가상각비·차량유지비·지급수수료·소모품비·복리후생비·운반비·광고선전비·여비교통비 + 제조업 전용 3종, 자산 5종).
  - 부가세 자동계산: 증빙유형별 과세/면세 (세금계산서·신용카드·현금영수증만 과세),
    공급가액 × 10% **원 단위 절사(intdiv)**, 귀속연도별 세율 룰셋 분리.
  - 스키마 힌트: `business_id` + `fiscal_year` + `supply_amount`/`vat` 분리 + `evidence_type` + soft delete.
- 참고 가치: **최고 (한국형 계정·부가세)**. /easy `category` 자유입력 → 간편장부 계정 목록화,
  `amount` 단일 → 공급가액/부가세 분리 확장의 직접 근거.

## 3. Central-MakeUs/Easybud-Server — Spring 복식부기 분할기입 (C)

- URL: https://github.com/Central-MakeUs/Easybud-Server · Java(Spring/JPA) · 라이선스 없음.
- 실제 구현: `Transaction 1—N Account` 분할, 금액 BigDecimal, 3단계 카테고리, 카드 연결.
- 참고 가치: 우리와 같은 스택의 분할기입 모델. 코드 복사 없이 구조만 참고.

## 4. sebae1/SBS — 간편장부 데스크톱 + 적격증빙 (A)

- URL: https://github.com/sebae1/SBS · Python/SQLite · Apache-2.0 → 재사용 가능.
- 실제 구현: `account_book`(수입/지출/고정자산, 공급가액/부가세 분리, 입금일자) +
  `supplementary`(견적서/거래명세서/**적격증빙** 파일 연결) + 엑셀 내보내기.
- 참고 가치: 증빙 3종 분류와 파일 명명 규칙(일자_유형_거래처_종류), 엑셀 export 항목.

## 5. tyohnn/holiday-cfo — Beancount식 계정·불변식 (B)

- URL: https://github.com/tyohnn/holiday-cfo · TypeScript · MIT.
- 실제 구현: `Assets:Bank:KB:Checking`식 코드=경로, 차변-양수 단일 부호 불변식(SUM=0),
  placeholder/마감일/화폐 플래그, ISO 날짜 엄격 검증.
- 참고 가치: 계정코드 설계와 단일합 불변식 아이디어. 한국 세법 없음.

## 6. bella-portfolio/double-entry-ledger — 교과서형 분개 검증 (C)

- URL: https://github.com/bella-portfolio/double-entry-ledger · Kotlin/Spring · 라이선스 없음.
- 실제 구현: Journal/Entry/Side + 차변합계=대변합계 검증 + 0원 거부 + 테스트.
- 참고 가치: 최소 분개 불변식 코드 형태. 한국 요소 없음.

## 7. HanTJ/budget-book — PHP DDD 원장 (C)

- URL: https://github.com/HanTJ/budget-book · PHP · 라이선스 없음.
- 실제 구현: Account/AccountType/Journal 도메인 + 마이그레이션.
- 참고 가치: 도메인 분리 형태만 참고.

## 8. junpakPark/spring-cashbook — Spring 시산표 미니앱 (C)

- URL: https://github.com/junpakPark/spring-cashbook · Java(Spring) · 라이선스 없음.
- 실제 구현: TransactionJournalizer + TrialBalance + 도메인 테스트.
- 참고 가치: 시산표 집계 형태만 참고.

## 9. sonjong980304-tech/journal-entry-test-agent — 분개 검증 에이전트 (C)

- URL: https://github.com/sonjong980304-tech/journal-entry-test-agent · Python(Streamlit) · 라이선스 없음.
- 실제 구현: 분개장·원장·시산표 자동생성+검증 벤치.
- 참고 가치: 자동분개 검증 아이디어. 엔진 아님.

## 11. lbiz-partners/hometax-doum — 홈택스 안내 스킬 (C)

- URL: https://github.com/lbiz-partners/hometax-doum · stars 51 · 라이선스 Other(재판매 금지).
- 회계 엔진이 아니라 신고 준비 안내(부가세·종소세·세금계산서·**학원 면세사업자 사업장현황신고**·미수금 점검).
- 참고 가치: 학원 면세사업자 신고 준비 항목(도메인 지식만). 코드 재사용 대상 아님.

## 12. jaehyuk-choi-KICPA/hometax-vat-filing-assist — 세무사 작성 VAT PoC (C)

- URL: https://github.com/jaehyuk-choi-KICPA/hometax-vat-filing-assist · Playwright+n8n · 라이선스 없음.
- 부가세 신고 반자동화 PoC. 코드 재사용 불가, 신고 흐름 참고용.

## 제외

- shoo99/jangbu: 빌드 산출물만 있어 로직 검토 불가.
- junhyeokkk/wonjang: NestJS 스켈레톤, 원장 코드 없음.
- Orphy-a/Odoo-Accounting: Odoo 통째 복사본.

## 해외 엔진 (공통 구조만, 세법 제외)

| 프로젝트 | 라이선스 | 등급 | 비고 |
|---|---|---|---|
| [pgr0ss/pgledger](https://github.com/pgr0ss/pgledger) | MIT | A | Postgres 구현 복식부기 원장 |
| [beancount/beancount](https://github.com/beancount/beancount) | GPL-2.0 | C | 텍스트 회계 원조, 구조만 참고 |
| [firefly-iii/firefly-iii](https://github.com/firefly-iii/firefly-iii) | AGPL-3.0 | C | 복사 금지 |
| [frappe/erpnext](https://github.com/frappe/erpnext) | GPL-3.0 | C | 복사 금지 |

---

## /easy v1과 비교

우리가 이미 가진 것: 사업체 분리, 수입/지출, 학생 원장, 월 청구, 수납, 미수금, 대시보드.
AICassa `ledger_entries`가 우리 `easy_transactions`와 거의 같은 단식+VAT분리형이라 확장 방향이 검증된다.

| 갖고 싶은 것 | 출처 | 상태 |
|---|---|---|
| 한국형(간편장부) 계정 목록 | AICassa AccountSeeder (MIT) | 다음 단계에 `category` 후보로 도입 가능 |
| 공급가액/부가세 분리 + 증빙유형별 자동계산(절사) | AICassa VatCalculator + SBS 공급가액/부가세 | 설계 반영 후보 (세율 별도 검증 필요) |
| 적격증빙 3종(견적서/거래명세서/적격증빙) 분류 | SBS (Apache-2.0) | `receipt_ref` 확장 후보 |
| 분개장·원장·시산표·결산 구조 | rhl0509/erp (C, 구조만) | 복식부기 확장 시 설계서로 사용 |
| 차변=대변 불변식·멱등 전표 | rhl0509/erp, bella (C) + holiday-cfo (MIT) | 확장 시 적용 |
| 홈택스 신고 항목 지식 | hometax-doum (C) | 학원 면세사업자 항목 참고용 |

학생·수납 기능은 /easy 우선 유지 (조사 대상에 동등 기능 없음).

---

## 결론

- 한국 회계 GitHub 조사 결과
- 실질 후보: 1. rhl0509/erp (복식부기 설계) 2. aivance-kr/AICassa (간편장부+부가세, MIT) 3. sebae1/SBS (증빙+엑셀, Apache-2.0)
- 가장 참고할 가치가 높은 프로젝트: rhl0509/erp (구조) + aivance-kr/AICassa (직접 재사용)
- 직접 재사용 가능한 코드: aivance-kr/AICassa (MIT), sebae1/SBS (Apache-2.0), tyohnn/holiday-cfo (MIT), pgr0ss/pgledger (MIT) — 단 복사 전 해당 부분 라이선스 고지 유지
- 구조만 참고할 것: rhl0509/erp, Easybud-Server, bella, HanTJ, junpakPark, journal-entry-test-agent, beancount/firefly/erpnext
- 한국형 계정과목 참고 가능: AICassa (국세청 간편장부 체계) — 현행 고시 대조 후 사용
- 복식부기 엔진 참고 가능: rhl0509/erp (전기형+재대사+마감), holiday-cfo (단일합 불변식)
- 부가세/세무: AICassa 10%·절사·증빙구분은 **현재 법령 별도 검증 필요**. hometax-doum은 안내용이라 근거 아님
- 현재 /easy에서 그대로 유지: 사업체 분리, 단식 거래, 학생 원장, 월 수납·미수금, 대시보드
- 새로 가져올 가치가 높은 기능: 간편장부 계정 목록화, 공급가액/부가세 분리, 증빙 3종, 엑셀 내보내기
- 한국 프로젝트가 충분한가: NO — 실사용급 복식부기 엔진 중 허용 라이선스(MIT/Apache) 프로젝트가 없음. 단식·간편장부급은 AICassa/SBS로 충분
- 다음 단계: 이지샵 2년치 확보 후 실제 계정과목 대조 → 간편장부 계정 목록 + 공급가액/부가세 분리 설계 (세율 별도 검증과 함께)
