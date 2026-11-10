# 10 — 첫 실제 연동: 은행 입출금 (CODEF 수시입출 거래내역)

> **2026-11-10 설계 변경:** CODEF 정식 사용은 유료 B2B 계약(견적 필요)이라, 계약 전에는 [11 — 기관 사이트 직접 받기 (MCP)](11_MCP_SITE_COLLECTION.md)를 기본 경로로 쓴다. 아래 CODEF 어댑터는 그대로 두되 `UNVERIFIED`로 꺼져 있다.

> 작성 2026-11-10. 공식 개발가이드(developer.codef.io)의 상품 메타데이터와 공식 샘플(github.com/codef-io/codef-python)에서 **직접 확인한 항목만** 구현했다. 계약·키·실호출 검증 전이므로 readiness는 `UNVERIFIED`이고 자동수집은 실행되지 않는다.

## 1. 확인한 계약 (근거: developer.codef.io 상품 상세 API, 2026-11-10 조회)

| 항목 | 기업 | 개인 |
|---|---|---|
| 상품코드 | `KR_BK_1_B_002` 수시입출 거래내역 | `KR_BK_1_P_002` |
| endpoint | `POST /v1/kr/bank/b/account/transaction-list` | `POST /v1/kr/bank/p/account/transaction-list` |
| 인증 | 공동인증서 로그인으로 만든 connectedId, 추가 인증 없음 | 공동인증서, 추가인증(`CF-03002`) 가능 |
| 필수 입력 | organization, connectedId, account(숫자만), startDate, endDate(yyyyMMdd), orderBy("0" 최신/"1" 과거) | 동일 |
| 응답 | `data.resTrHistoryList[]`: resAccountTrDate, resAccountTrTime, resAccountOut, resAccountIn, resAccountDesc1(보낸분/받는분)·2(거래구분)·3(적요)·4(거래점), resAfterTranBalance, (기업)resCounterAccount | 동일(일부 계좌 필드 추가) |
| 조회 가능 기간 | KB국민 최근 10년(12개월 단위), 신한 2009년 1월부터 | KB국민 제한없음(12개월 단위), 신한 제한없음 |
| 과금 | resTrHistoryList 5,000건 단위 (3,000건=1회, 5,500건=2회) | 동일 |
| 주의 | 과도한 호출 시 대상기관이 IP 차단 가능 — 배치형 남용 금지 | 동일 |

공통 프로토콜(공식 샘플): 토큰 `POST https://oauth.codef.io/oauth/token` (Basic client_id:secret, `grant_type=client_credentials&scope=read`, 유효 1주), 상품 호출 본문은 JSON을 UTF-8 URL 인코딩, 응답도 URL 디코딩 후 JSON. `result.code=CF-00000` 성공. 운영 `https://api.codef.io`, 개발 `https://development.codef.io`, 샌드박스 `https://sandbox.codef.io`.

오류 분류(공식 오류코드 목록에서 확인): `CF-03002` 추가인증, `CF-04xxx` connectedId 문제, `CF-123xx/128xx/112xx` 인증서·비밀번호 → **재인증 필요(AUTH_REQUIRED, 자동 재시도 안 함)**. `CF-0999x`·HTTP 401 → 토큰 재발급 1회 재시도. `CF-00013` IP 미허용 등 → 설정 오류. `CF-13001` 기간 오류.

## 2. 왜 KB국민은행을 첫 기관으로 정했나

- 기관코드 `0004`가 CODEF 공식 샘플에 나온다. 신한은행 코드(`0088`)는 제3자 문서(토스 은행코드 표, CODEF 기반)에서만 확인 → 화면에 "기관코드 확인 필요"로 표시.
- 둘 다 조회 기간 조건이 넉넉하고, 상품 구조는 같아 설정값(기관코드)만 바꾸면 된다.

## 3. 구현 위치

- `CodefApiClient` — 토큰 캐시, URL 인코딩, 401/토큰 오류 1회 재시도, 결과코드 분류(코드만 기록, 원문·자격증명 미기록).
- `CodefBankTransactionAdapter` — `EasyPeriodCollectionAdapter`(31일 구간, 처음엔 90일 전부터). 원본 키는 고유번호가 없어 내용 해시 + 응답 내 등장 순번.
- `EasyBankTransactionClassifier` — 카드대금·정산입금·정책자금·지원금·권리금·현금영수증 판정(`docs/easy/ACADEMY_TAX_CHECKLIST.md`).
- 화면 `/easy/connections`, API는 `docs/API.md` "수집 즉시 장부 저장·은행 연결 관리".

## 4. 실제 연동에 필요한 사용자 작업 (유료 계약·인증 등록은 대표님 확인 후)

1. codef.io 가입 → 정식(운영) 이용 신청(사업자 정보·프로젝트 등록, 운영팀 검토 약 3영업일) → B2B 계약. **단가 비공개(견적 필요)** — 공식 문서로 확인된 사실은 "거래내역 5,000건 단위 과금"뿐.
2. 발급된 `CODEF_CLIENT_ID`/`CODEF_CLIENT_SECRET`을 EC2 `/etc/f2r/env`에 등록(코드·DB·Git 금지). 대시보드에 EC2 공인 IP 허용(`CF-00013` 방지).
3. KB국민은행 공동인증서로 CODEF 계정 등록(`/v1/account/create`, 인증서 파일·비밀번호는 CODEF로만 전송 — F2R은 받지도 저장하지도 않음) → 받은 `connectedId`를 `/easy/connections`에 등록.
4. 개발 환경(development.codef.io)에서 1개월 구간 실호출 → 응답 필드·건수·잔액 대조.
5. 확인되면 운영 설정 `easy.collection.verified-sources=CODEF_BANK` → 화면에서 "자동수집 켜기".

## 5. 준비만 된 기관 (실제 연동 아님)

| 수집원 | 상태 |
|---|---|
| 신한은행 입출금 | 같은 어댑터로 가능, 기관코드 공식 확인 필요 |
| 신한카드·KB카드 사용/취소/청구 | CODEF 카드 상품 존재(승인/청구). 어댑터 미구현. 취소→원거래 연결·역분개 저장 경로는 공통 코어에 구현됨 |
| 여신금융협회 카드매출·정산 | `CrefiaCardsalesAdapter` 골격, 이용기관 승인 필요 (09 문서) |
| 홈택스 현금영수증·전자세금계산서 | CODEF/팝빌 후보 (03·06 문서), 미구현 |
