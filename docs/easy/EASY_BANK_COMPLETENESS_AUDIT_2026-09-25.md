# EASY 은행계좌 장부 완전성 조사 — 2026-09-25 (READ ONLY)

> 신규 개발 없음, 은행 로그인 없음, 수집 없음. 집계·구조만 기록 (계좌번호·학생명·입금자명 없음).

## 1. 코드 상태 (main)

- BANK FOUNDATION: PARTIAL — EasyBankAccountMap entity+repo 존재 (V1002)하나 service/controller 사용처 0건.
  easy_bank_account_maps 테이블은 운영에 없음 (스키마 V1000까지).
- BANK ACCOUNT MAP: 코드만 (운영 count 0 — 테이블 부재).
- BANK IMPORTER: EasyShop deposit_withdrawal funds 경로만 (수동 JSON, P&L 제외·보존). 은행 전용 파서 없음.
- BANK COLLECTOR: 없음 (스케줄러 isVerified 게이트로 차단, CREFIA fail-closed).
- BANK POSTING: 코드만 (EasyRealPostingService 명시계정 호출, 미배포·미사용).

## 2. 운영 실측 (census 36107713327 재사용)

- TOTAL 669 (학원 657 + 출판사 12). SOURCE: EASYSHOP 645 / MANUAL 24 / BANK 0 / CARD 0.
- TUITION: 0건 (연결 구조 자체가 없음 — EasyTuitionPayment에 txn FK 없음).
- CARD_EVENTS: 0건. 은행 원본 테이블 없음.

## 3. 분류

- VERIFIED_IN_LEDGER: EASYSHOP funds 입출금 보존행 (금액은 원본“There”에만 — 행수 미집계, 후속 쿼리 필요).
- PRESENT_BUT_SOURCE_AMBIGUOUS: MANUAL 24건 (pay_method·category 내역 미조회 — 후속 쿼리 필요).
- NOT_IMPORTED: Shinhan/KB 실파일 (타 PC, 대조 보류 중).
- SOURCE_NOT_AVAILABLE: 은행 원본 (수집 수단 없음).
- CAPABILITY_MISSING: 은행 파서/수집기, 계좌 매핑 운영 등록, 사업주 입출금 계정 (seed 없음), 수납↔장부 연결.

## 4. 사업주·출판사

- 사업주 입출금 구조: 없음 (OWNER_CAPITAL은 미배포 V1002에만). 건수 UNKNOWN.
- 출판사 12건: 전건 EXPENSE·POSTED, source 내역 미조회 (후속 쿼리 필요). BANK VERIFIED: NO.

## 5. 후속 쿼리 (census 확장 시)

- MANUAL 24건의 pay_method·category·source_ref prefix 집계
- EASYSHOP_FUNDS 행수·입출금 합계
- 출판사 12건의 source_kind 내역
- (신규 테이블 없음 — 기존 컬럼 집계만)
