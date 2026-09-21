# 09 — 카드매출 Provider 판정 + 매통조 어댑터 구현 기록 (2026-09-21)

> 02~08 후속. 공식 문서 기준으로 재확인한 provider 비교 판정 + 이번 라운드 구현물 정리.
> 실제 외부 API 호출 없음, credential 없음, production 변경 없음.

## 1. Provider 비교 (공식 근거 기준)

### PROVIDER: 여신금융협회 매통조 OpenAPI (openapi.crefia.or.kr)
- OFFICIAL API: 있음 (VERIFIED — `/cfaApi/apiIntro` 직접 fetch: 승인/매입/입금 paging 4종, OAuth2 client_credentials, 토큰 86400초, 게이트웨이 `:24002`, `/oauth/token` + `/oauth2/mer/page` 예시 공개)
- ELIGIBILITY: 이용기관 신청 필요 — 회원가입 → 사업계획서+첨부서류 → 송수신테스트 → 심사(사업모델 적정성+보안안정성 점검) → 약정 → 권한부여. 현 이용기관 10곳은 금융기관/대형핀테크(토스뱅크·비바리퍼블리카·롯데·IBK·NH·카카오뱅크·한국평가정보·토스페이먼츠·신한·페이민트). 소규모 법인의 심사 통과 불확실
- AUTH: OAuth2 client_credentials (Basic client_id:secret) + 가맹점별 "정보 전송 이용 동의" 필수 (자체인증기관 동의/취소 API 제공)
- DATA: 승인(승인번호·금액·거래일자·시간·할부) / 매입(카드사·가맹점번호·매출금액·수수료·지급금액·매입일자·지급예정일·부가세대리납부) / 입금(카드사·실입금액·보류금액·결제계좌·입금일자) — 작업지시 필드 전부 커버 (VERIFIED — crefia.or.kr 신용정보활용체제 공시)
- COST: "일정량의 수수료" 부과 명시 (VERIFIED — 포털 main). 요율 비공개 (가입 후 확인)
- SANDBOX: 송수신테스트 절차 존재 (가입 후). 공개 sandbox URL 없음
- LIMITATION: 일일 호출 제한 존재 (수치 비공개), 상세 스키마·백필 한도·쿼터는 가입 후 열람
- EASY FIT: 유일한 공식 가맹점 매출 경로. SPI 구조(승인/매입/입금 3분할, 승인번호 dedupe)와 1:1 대응
- VERDICT: **CONDITIONAL** (이용기관 승인 + 수수료 + 가맹점 동의 UX가 선행조건)

### PROVIDER: CODEF (헥토데이터)
- OFFICIAL API: 카드/은행/홈택스 상품 실재 (VERIFIED — SDK·개발가이드). 단 여신협회 가맹점 매출 상품은 2023 소개서에만 존재 기록, 현재 메뉴 YS 비어있음 (02·06 조사)
- ELIGIBILITY: B2B 계약 (사업자+프로젝트 등록 → 3영업일 승인)
- AUTH: OAuth2 + connectedId (clientType P/B), CF-03002 재인증 흐름
- DATA: 법인 승인/매입/청구 (보유카드 측). 가맹점 매출 디테일 미확인
- COST: rate card 비공개 (견적), sandbox 무료/일 100회
- SANDBOX: 있음 (sandbox.codef.io, 고정 응답)
- LIMITATION: YS 상품 현재 판매 여부 불명, 단가·SLA 비공개
- EASY FIT: BusinessCard/은행 경로 후보. 카드매출 본경로 아님
- VERDICT: **NEEDS VERIFICATION** (YS 상품 판매 여부 + KR_PB_NT_071 디테일, 벤더 문의 필요)

### PROVIDER: 팝빌
- OFFICIAL API: 있음 (VERIFIED — developers.popbill.com: 홈택스수집 RequestJob/Search, 현금영수증, easyfinbank 계좌조회)
- ELIGIBILITY: 파트너 등록 (LinkID/Secret, IP 바인딩) + 고객 사업자의 연동회원 가입
- AUTH: LINKHUB 서명 + 토큰. 부서사용자 계정 방식이 인증서 불요·안정
- DATA: 세금계산서·현금영수증 매입/매출, 계좌 입출금. **카드매출(가맹점) 제품 없음** (VERIFIED — 카탈로그 부재)
- COST: 공개 (홈택스수집 3만원/월×2, 계좌 5천/월)
- SANDBOX: 테스트 환경 무료 (운영 동일 동작)
- LIMITATION: 카드매출 미지원
- EASY FIT: Hometax 어댑터 후보 (PHASE 3). 카드매출 아님
- VERDICT: **NOT SUITABLE** (카드매출용)

### PROVIDER: 하이픈 / 스크래핑 중계
- OFFICIAL API: 여신협회 기준 16종 API 존재하나 **"스크래핑 방식" 명시** (VERIFIED — hyphen.im)
- EASY FIT: 협회 약관 제22조 3호 스크래핑 명시 금지와 충돌
- VERDICT: **NOT SUITABLE** (약관 리스크 — EASY 원칙상 배제)

### PROVIDER: 바로빌 카드조회
- OFFICIAL API: 있음 (VERIFIED — dev.barobill.co.kr: 승인/매입 수집, 카드사 홈페이지 기반 수집 보관)
- EASY FIT: 보유카드(지출) 조회. 가맹점 매출 아님
- VERDICT: **NOT SUITABLE** (카드매출용)

## 2. 최종 후보

**매통조 OpenAPI 직접 이용기관 신청** (CONDITIONAL). 중계(CODEF/하이픈) 경유보다 종속 감소·근거 명확.
단 운영 호출은 이용기관 승인 전까지 BLOCKED이므로, 이번 라운드는 **프로토콜 충실 클라이언트 + SPI 어댑터**
(호출 코드 없이 테스트 가능한 구조)까지 구현하고 production 활성화는 하지 않는다.

## 3. 구현물 (이번 라운드)

| 파일 | 내용 |
|---|---|
| `service/CrefiaCardsalesClient.java` | 토큰 발급(86400초 캐시)+페이징 조회+재시도(5xx·타임아웃 1회)+401 재발급 1회. baseUrl·경로 주입 가능. secret 로깅 없음 |
| `service/CrefiaAuthException.java` | 401/403 전용 — 메시지에 "401"/"인증" 유지 → 스케줄러 NEEDS_REAUTH 분류와 호환 |
| `service/CrefiaProviderException.java` | 비인증 실패 (타임아웃·5xx·파싱) — NEEDS_REAUTH로 오분류되지 않음 |
| `service/CrefiaCredentialProvider.java` | 자격증명은 환경변수(CREFIA_CLIENT_ID/SECRET)로만 주입. 없으면 어댑터가 명시 차단 |
| `service/CrefiaCardsalesAdapter.java` | SPI 구현, 코드 `CARDSALES` (V983 시드와 일치). 승인/매입/입금 → INCOME 행, externalId=승인번호/매입지문/입금지문, rawJson에 provider·account·occurredAt·amount·counterparty·description·sourceType·collectedAt 봉투. confirm 없음, 직접 INSERT 없음 |
| `service/EasyCollectionIngestService.java` | `resolveAdapter`에 1 case 추가 (공통 코어·락·경계 변경 없음) |

SPI 보존: 외부 호출은 트랜잭션 밖(fetch)→RUNNING 락→ingestParsedRows 공통 코어→staging. Provider 코드는
EasyImportService에 박지 않고 어댑터에 격리. 기존 `CARDSales` 스켈레톤·테스트 무변경.

중복 방지: 1차 EXT UNIQUE (`CARDSALES:{biz}:{conn}:EXT:{승인번호|지문}`) + 2차 tuple + 3차 장부 fingerprint
(그대로). 재수집은 전량 DUPLICATE_EXCLUDED — 테스트로 증명.

## 4. 테스트 (신규 30, 전부 mock/fixture — 실제 호출 0)

- `CrefiaCardsalesClientTest` 13: 토큰 캐시·401·재발급 재시도·페이징·빈 결과·500 재시도·malformed·timeout·secret 비노출
- `CrefiaCardsalesAdapterTest` 14: SPI 계약·3종 매핑·지문 폴백·빈 결과·malformed error행·자격증명 차단·ref 검증·무네트워크 validate·limit·since·중복 안정성
- `CrefiaCardsalesIngestTest` 3: 수집→staging 관통+무 auto-confirm, 재수집 전량 제외, IMPORTED(fingerprint) 보호

EASY 회귀: easy 패키지 24 suites / 166 tests / failures 0 / errors 0. bootJar PASS.

## 5. 다음 단계 BLOCKER (코드 우회 없이 보고)

1. 협회 이용기관 신청 (자격·사업계획서·수수료율·쿼터·상세 스키마·백필 한도 — 가입 후 확인)
2. 가맹점 전송동의 수집·만료 관리 UX (자체인증기관 동의/취소 API 연동 포함)
3. CODEF YS 상품 판매 여부 벤더 문의 (02-6953-0257 / biz@hectodata.co.kr)
4. 승인 후: dataset 상세 경로 확정 → `*_PATH` 상수 교체 → sandbox 실호출 검증 (별도 승인 필요)
