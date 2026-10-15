# 06 — CODEF / 팝빌 / 대안 경로 비교

> 조사일: 2026-09-20 (PHASE 0). 근거 등급: VERIFIED(1차 자료 직접 확인) / UNKNOWN(미검증).

## 0. ⚠️ 전제 정정 (매우 중요)

**"CODEF = 금융결제원 API중계, codef.or.kr"이라는 작업 지시의 전제는 틀렸다.**
- `www.codef.or.kr`은 한국 금융 서비스와 무관 (fetch 실패, Codeforces 노이즈만) — VERIFIED(음성 확인)
- CODEF API는 **민간회사 (주)코드에프 운영 → 2023-02-14 (주)헥토데이터로 상호 변경**(헥토파이낸셜 그룹, 공식 소개서 PDF p.14) — VERIFIED ([2023-08 서비스소개서 PDF](https://codef.io/assets/pdf/202308_CODEF%20API%20%EC%84%9C%EB%B9%84%EC%8A%A4%EC%86%8C%EA%B0%9C%EC%84%9C.pdf))
- 사이트: codef.io / developer.codef.io / oauth.codef.io / api.codef.io
- **금융결제원(KFTC)의 오픈뱅킹(openapi.kftc.or.kr)·마이데이터는 별도 경로**이며 CODEF와 무관. KFTC는 CODEF가 중계하는 '기관 중 하나'일 뿐.
- 마이데이터 전문사업자 라이선스 보유 주장 — 단일 출처로 LIKELY ([코드에프 vs 쿠콘](https://www.techbaekgwa.co.kr/codef-vs-cocone/))

## 1. CODEF 상품 카탈로그 (developer.codef.io 백엔드 메뉴 API 실조회, 43개 카테고리·~427개 상품 route) — VERIFIED

### 1.1 은행
- 개인/기업 모두: 보유계좌, 빠른조회, 수시입출 거래내역, 적금·대출 거래내역; 기업전용: 외화 거래내역, 이체결과, 계좌비밀번호 검증 등. 저축은행 별도 그룹.
- 은행 코드 커버리지(25행, Toss Payments가 CODEF 기반으로 공개): KB004·IBK003·하나005/081·우리020·NH011·신한088·케이뱅크089·카카오뱅크090·토스뱅크092 등 — VERIFIED ([Toss bank-code guide](https://docs-pay.toss.im/guide/bank-code)). 상품별 지원矩阵는 SPA라 미확인 [UNKNOWN].

### 1.2 카드 (보유자 측)
- 개인: 보유카드, **승인내역**, **청구내역**, 한도/실적/등록여부
- **법인: 당일승인내역, 매입내역, 선결제내역, 승인내역, 청구내역, 한도조회** — VERIFIED (menu API)

### 1.3 여신금융협회 가맹점 카드매출 — 핵심 질문의 답
- **2023-08 공식 PDF에는 상품 실재**: "여신금융협회(가맹점 매출거래정보) — 기간별 승인내역/매입내역/**입금내역**, 월별 매입내역" — VERIFIED (PDF p.5)
- **현재 문서 메뉴 API에서 "YS 여신금융협회" 카테고리는 있으나 상품 목록 비어있음** (`"categoryID":"YS","menuInfoVoList":{}`) — VERIFIED → 현재 **비공개/계약 한정/개편/폐지 중 하나**. **アーキテク처 확정 전 CODEF(02-6953-0257, biz@hectodata.co.kr) 직접 확인 필수**.
- 대체 1: CODEF의 **홈택스 기반 "신용카드 매출자료 조회"** (menuCode `KR_PB_NT_071`, `/products/public/each/c/credit-card-sales-data-list`) — VERIFIED. 가맹점 카드매출 총액/전표 데이터를 홈택스 발행수신 자료로 얻는 구조. 자동수집 목적에 충분할 가능성 높으나 승인번호 단위 디테일은 미확인.
- 대체 2: **여신금융협회 자체 공개 OpenAPI 실재** ([openapi.crefia.or.kr](https://openapi.crefia.or.kr/useApp/signUpMain), 2022-04-20 오픈, 가맹점 동의 기반, 무료→과금체계 수립 논의) — VERIFIED ([기사](https://www.digitaltoday.co.kr/news/articleView.html?idxno=441952)). 02번 문서에서 상세.

### 1.4 국세청 홈택스 (가장 풍부한 그룹) — VERIFIED (menu API route명 기준)
- **전자세금계산서 조회 계열**: 등록여부, 발급목록, **통합조회(taxinvoice-integrated-list)**, 합계표, 상세, 기간별 매출/매입 통계, 거래처관리, 제3자 발급사실 조회 → **타 사업자의 매입·매출 세금계산서 '조회' 지원** (발행 전용 아님)
- **발행/수정/취소는 팝빌 경유** (`popbill-taxinvoice-*`, `popbill-cashbill-*` 라우트명 확인) → CODEF와 팝빌은 경쟁이자 파트너
- 현금영수증: 사용/매입/매출내역, 매출누계, **사업용신용카드 매입세액공제 확인/변경**
- 증명발급: 소득금액·사업자등록·부가세과세표준·납세증명 등 다수
- 신고서: 부가세/종소세/법인세/원천징수 신고서, 파일변환신고, 경정청구, 환급조회
- 기타: 사업자 휴폐업 상태, 사업자등록번호 조회, 나의 세무대리인 수임/해임 동의

## 2. CODEF 인증 모델 — VERIFIED (codef-io 공식 SDK/README)

1. **플랫폼 인증**: OAuth2 client_credentials (`POST https://oauth.codef.io/oauth/token`, client_id/secret는 codef.io 키 관리에서 발급) → JWT access_token, **만료 1주(604799s), 재사용·자동갱신**. 민감 필드는 RSA(PKCS1) 공개키 암호화. (접속토큰 개인키 서명 방식은 공식 코드에서 미확인 — 지시문 전제와 다름)
2. **connectedId 수명주기**: `/v1/account/create`(accountList: countryCode, businessType, **clientType P(개인)/B(기업)**, organization, loginType 0=공동인증서(derFile/keyFile 업로드)/1=ID·PW) → `connectedId` 발급 → `/v1/account/add`(기관 추가) / `/v1/account/update`(비밀번호 변경→신규 id) / `/v1/account/delete`(기관별 즉시 revoke) / `/v1/account/connectedId-list`. **기관별 clientType당 자격증명 1건** 제약.
3. **OTP/추가인증**: 응답 코드 **CF-03002** + transactionId → 사용자 인증 완료 후 `requestCertification`(v1)/`requestSimpleAuthCertification`(v2) 재호출. 다건요청은 `codef-io/multiple-request` 샘플.
4. **사업자(법인) 계좌/카드/홈택스: clientType B로 end-to-end 지원 확인** — VERIFIED. 1원 이체 계좌인증도 사업자번호(10자리) 지원 [헥토데이터 블로그](https://blog.hectodata.co.kr/codef_api_1won).

## 3. Java/Spring 통합

- **공식 오픈소스 SDK(MIT)**: `io.codef.api:easycodef-java:1.0.6`(Maven Central, v1) / **`easycodef-java-v2:2.0.0-beta-005` (JDK 21+, Spring Boot 3 singleton-bean 패턴 문서화, 토큰 자동갱신, builder API)** — VERIFIED ([github.com/codef-io](https://github.com/codef-io), [V2 wiki](https://github.com/codef-io/easycodef-java-v2/wiki)). EASY(Java/Spring)에 직접 매핑 가능.
- REST는 평범한 JSON POST (`api.codef.io` 실서비스 / `sandbox.codef.io` 목업 고정 응답 / development 환경).
- **dedupe에 쓸 응답 필드**: 은행 `resAccountTrNo`, 카드 `resCardApprovalNo`, 세금계산서 `resAuthorizationNo` (05번 문서 jangbu 필드 사전과 교차 확인).

## 4. 가격·제한

- **무료**: 샌드박스 가입 즉시; 데모 환경 **일 최대 100회** — VERIFIED (PDF p.12)
- **정식**: 사업자 정보+프로젝트 등록 → 운영팀 검토 → 3영업일 승인 → B2B 계약 — VERIFIED (가격과 무관한 진입 절차 확정)
- **단가**: 공개 rate card 없음(로그인 필요). "건당 과금" 주장 [헥토 블로그]과 "월 구독" 보도 [etnews] 혼재 → **정확한 금액 UNKNOWN, 견적 필수**. 3rd-party 비교("건당 ~10원+월 최소 80만원")는 [C] 수준.
- **운영 TPS/분당 제한**: 공개 문서 없음 UNKNOWN. SLA: "365x24" 마케팅 문구 외 공식 SLA%·보상 조항 없음, 공개 장애 포스트모텀 없음 UNKNOWN. 지원: 평일 08:00–17:00 게시판.

## 5. 팝빌 (Popbill / linkhub) — 집중 조사 완료

### 5.1 수집 관점 제품
- **홈택스수집 API** ✅ VERIFIED: "국세청 홈택스에 신고된 전자세금계산서·현금영수증의 **모든 매입/매출 내역** 수집" — 실시간(비동기 잡) 크롤링 방식, 별도 설치 불요. 카드매출·부가세 신고자료는 범위 미기재=미지원(추정). 플로우: `RequestJob → GetJobState → Search/Summary/GetTaxinvoice`.
- **계좌조회 easyfinbank** ✅: 20개 은행 입출금/잔액, 거래내역은 **현재 기준 과거 3개월 이내, 1회 호출 최대 1개월**. **빠른조회** = 공동인증서 없이 은행 빠른조회 신청+**비밀번호 4자리** 등록.
- **카드매출 제품 없음** ✅ (카탈로그 부재 확인) — 카드매출은 02번 매통조 경로가 유일한 공식 채널임 재확인.
- 예금주조회(성명 50원/실명 80원), 사업자등록상태 10원, 전자세금계산서 발행 200원/건, 현금영수증 0원/건 ✅.

### 5.2 인증·대리 조회 ✅
- API 인증: 파트너 등록 → LinkID/SecretKey, 신 REST는 `Authorization: LINKHUB {LinkID} {Signature}` + 토큰, **IP 바인딩**. (구버전 IP+ts+salt SHA512 설은 원문 미확인=UNKNOWN)
- **3자(고객사) 데이터 대리 조회 가능하되 이중 전제**: ① 고객 사업자가 팝빌 **연동회원** 가입 필수 ② 홈택스수집은 실제 사업자 인증정보 등록 필요 — **공동인증서 방식은 타인 홈택스 로그인 시 연결 중단**, **부서사용자 계정 방식은 인증서 불요·동시성 안정**([인증관리 가이드](https://developers.popbill.com/guide/httaxinvoice/introduction/manage-cert)) → EASY UX상 부서사용자 방식이 결정적 대안.

### 5.3 요금 ✅ ([Pricing](https://www.popbill.com/Pricing), VAT 포함, 선불 포인트, 최소결제 11,000원)
| 항목 | 요금 |
|---|---|
| **홈택스수집 전자세금계산서** | **30,000원/월** |
| **홈택스수집 현금영수증** | **30,000원/월** |
| 계좌조회 | 5,000원/월 |
| 빠른조회 | 조회주기(3~30분)별 차등, 공식 요금표 미기재 — UNKNOWN |
| API 연동료 | 개별 협의제 |
- 테스트 환경 무료(운영 동일 동작, 실서비스에만 과금), 테스트 공동인증서 무료 발급 ✅. 고객센터 평일 09–17, 공개 SLA/status page 미발견 ❓ UNKNOWN.

## 5.4 기타 대안 (세금 릴레이 니치)
- **바로빌**: 전자세금계산서+계좌+**홈택스 매입/매출 조회(스크래핑 명시)**, 카드 이용내역 조회 상품, "연동비 0원 무료 테스트" — 요금 원문 미확인 [U] ([services](https://dev.barobill.co.kr/services/category), [card](https://dev.barobill.co.kr/services/card))
- **하이픈**: 펌뱅킹/계좌 커넥터 강점, 홈택스 수집 제품 확인 불가 — 세무 릴레이 부적합(추정). 단 02번 문서의 여신협회 기준 카드매출 16종 API 보유
- **쿠콘**: 150+ 범용 데이터 중계(휴폐업/사업자정보), 세금계산서 수집 중심 아님

## 6. 5개 경로 비교 매트릭스 (사실 기반; 최종 점수 아님)

| 항목 | CODEF(헥토데이터) | 팝빌 | 직접 연동(기관별 공식API) | 파일 자동/반자동 Import | 브라우저 자동화 |
|---|---|---|---|---|---|
| 지원기관 | 은행25+/카드9+/홈택스 다종/증명/신고/보험/증권/공공 — VERIFIED | 홈택스수집(세금계산서·현금영수증)+은행20+예금주/사업자상태 — 카드매출 없음 VERIFIED | KFTC 오픈뱅킹(8행+, 법인 조회 2025~), KB 기업OpenAPI, NH 오픈플랫폼, 여신협회 OpenAPI, NTS 공공데이터 — 04/02/03 문서 | 사용자 다운로드 파일 전부 | 카드/은행/홈택스 홈페이지 |
| Java 난이도 | 공식 SDK(v2, Spring Boot3) — 낮음 | 공식 Java SDK+비동기 잡(RequestJob/Search) — 낮~중 | KFTC/OpenAPI별 자체 클라이언트 — 중~높음, 인가 절차 큼 | 파서 구현 — 낮음(EASY 검증됨) | Playwright+인증서 리버스 — 높음·fragile (hometaxbot 선례) |
| 운영 안정성 | SLA 비공개 UNKNOWN | 공개 SLA/status 미발견 UNKNOWN, 점검 공지 존재 | 각 기관 공식 SLA | 자체 파싱 실패 리스크만 | 사이트 개편에 즉시 파손, CAPTCHA·2FA 제약 |
| 비용 | 미공개 견적(B2B 계약 필수) | **공개 요금**: 홈택스수집 3만원/월×2과목, 계좌 5천/월, 선편 포인트 — VERIFIED | 인프라+전자금융업 등록 비용 | 사실상 무료 | 무료지만 유지비용 최고 |
| 인증 유지 | connectedId+재인증(CF-03002), 인증서 업로드 기관은 갱신 필요 | **공동인증서 방식은 타인 로그인 시 연결 중단 / 부서사용자 계정은 인증서 불요·안정** — VERIFIED | OAuth 토큰/앱키, 사용자 동의 | 없음 (사용자 매번 업로드) | ID/PW·인증서 **자체 보관 → 대법 2020도9972 리스크** |
| 법적 리스크 |集中기관으로서 동의 기반 수집 (계약 확인) | 동종 | 인가(전자금융업/마이데이터) 필요 시 저-risk | 무-risk | **약관/전금법 위반 소지 큼 — EASY 원칙상 배제** |

### 법률 리스크 노트
- 신용정보법/전자금융거래법상 **대리접속·접근매체 보관 금지**가 전 경로에 공통 제약(→04번 §1.3). 자동화 판단 기준은 "누가 고객 자격증명을 보유·관리하는가".
- 여신협회/cardsales·홈택스 스크레이핑은 약관 위반 소지로 EASY 정책상 후보에서 제외(로그인/CAPTCHA 우회 금지 전제).

## 7. CODEF 채택 시 잔여 확인 목록 (벤더 문의 필수)
1. YS 여신금융협회 가맹점 카드매출 상품의 현재 판매 여부/필드/가격
2. KR_PB_NT_071 '신용카드 매출자료 조회'의 실제 반환 디테일(승인번호/수수료/지급예정 포함 여부)
3. 상품별 건당 단가 + 월 최소 계약, 운영 레이트리밋, SLA 조항
4. 홈택스 상품별 요구 인증(loginType 0/1, OTP 주기)과 connectedId 재인증 주기
5. 카드사별/은행별 지원矩阵 (demo 계정 생성 후 실호출 검증)

> 팝빌 관련 확인 항목은 §5.3에서 해결됨(요금 공개). 잔여: API 연동료 협의, 빠른조회 주기별 요금, SLA 조항.
