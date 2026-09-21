# Easy Auto Collection Phase 3 Handoff

## Status
Phase 3 provider research interrupted due to model reasoning loop.
Current work preserved for continuation.

## Existing implementation (Phase 2 complete)
- EasyCollectionAdapter SPI
- EasyCardsalesAdapter skeleton
- EasyCollectionConnection
- ingestParsedRows
- external_id dedupe
- connectionRef opaque handling
- provenance/rawJson preservation
- no auto-confirm
- no direct easy_transactions insert

## Phase 3 research performed
조사한 provider 이름만 기록:
- CODEF (헥토데이터)
- 팝빌 (링크허브)
- 여신금융협회 매통조 OpenAPI
- KFTC 오픈뱅킹
- KB 기업OpenAPI
- NH 오픈플랫폼
- GitHub/open-source: kimlawtech/korean-jangbu-for, codef-io SDKs, finecodekr/hometaxbot

## Research topics already investigated
- 카드매출 (매통조 OpenAPI 승인/매입/입금)
- 카드매입/사업용카드 (CODEF 법인 승인/청구내역, 마이데이터 Card-008/014)
- 은행 거래내역 (오픈뱅킹, CODEF/팝빌 easyfinbank, KB 기업OpenAPI, NH 오픈플랫폼)
- 현금영수증 (CODEF 팝빌 배대, 팝빌 홈택스수집)
- 전자세금계산서 (CODEF 통합조회 taxinvoice-integrated-list, 팝빌 홈택스수집)
- 홈택스 관련 수집 (부서사용자 인증, 웹훅 1h 수집)
- API 인증 (OAuth2 client_credentials, connectedId 수명주기, OTP 재인증)
- 비용/가입조건 (CODEF 미공개 견적제, 팝빌 공개 요금 3만원/월×2, 오픈뱅킹 전자금융업 등록 필요)
- Sandbox (CODEF 데모 100회/일, 팝빌 테스트 1개월 무료)
- GitHub/open-source alternatives (korean-jangbu-for Apache-2.0, codef-io MIT SDKs, hometaxbot Apache-2.0)

## Existing research files
docs/easy/auto-collection/
  - 01_CURRENT_EASY_ARCHITECTURE.md
  - 02_CARDSALES_RESEARCH.md
  - 03_HOMETAX_RESEARCH.md
  - 04_BANK_CARD_RESEARCH.md
  - 05_OPEN_SOURCE_RESEARCH.md
  - 06_CODEF_POPBILL_COMPARISON.md
  - 07_AUTO_COLLECTION_ARCHITECTURE.md
  - 08_IMPLEMENTATION_ROADMAP.md
  - AUTO_COLLECTION_SOURCE_MATRIX.csv
  - PHASE1_COLLECTION_FOUNDATION_IMPLEMENTATION.md

## Unfinished
현재 확정하지 못한 내용:
- primary provider final selection
- exact production API contract (필드/응답 스키마)
- credentials/registration process completion
- real API integration implementation
- incremental collection cursor/token 설계
- scheduler production activation

## Safety
- production DB write 없음
- Batch5 confirm 없음
- production credential 없음
- main merge 없음
- deploy 없음

## Resume instruction
다음 Agent는 이 handoff와 기존 research 파일을 먼저 읽고
현재 상태에서 이어서 작업한다.
이미 수행한 provider 조사를 처음부터 반복하지 않는다.