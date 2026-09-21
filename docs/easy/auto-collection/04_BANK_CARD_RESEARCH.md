# 04 — 은행 / 사업용 카드 / PG 자동수집 조사

> 조사일: 2026-09-20 (PHASE 0). 근거 등급: **[V]**=1차 공식소스 확인, **[C]**=벤더/2차 자료 주장(계약 단계 재확인 필요), **[U]**=미검증.
> 핵심 결론: (1) **어떤 은행도 8개 전 은행을 커버하는 단일 직접 API는 없음**(KB 기업OpenAPI만 실재 확인). (2) **카드사 보유자(가맹점 아님)용 공식 거래내역 API는 사실상 없음** — 합법 경로는 마이데이터/집중기관 계약/사용자 업로드 3가지. (3) **자격증명 보관은 형사 리스크 선** — 대법원 2020도9972.

## 1. 은행 (사업용 계좌)

### 1.1 경로 비교

#### A. KFTC 오픈뱅킹 표준API (금융결제원 오픈뱅킹공동업무)
- 거래내역조회 응답 필드 [V](https://developers.kftc.or.kr/dev/openapi/open-banking/transaction): `tran_date`, `tran_time`, `inout_type`, `tran_type`, `print_content`(통장인자내용), `tran_amt`, `after_balance_amt`, 식별자 `api_tran_id`(API 생성)/`bank_tran_id`(금융기관 생성).
- **상대계좌/상대방/머천트 구조화 필드 없음** — 적요(print_content) 텍스트에서 파싱 필수 [V].
- 25건/회 페이지네이션(`befor_inquiry_trace_info` 연속조회), OAuth2 Bearer + scope=inquiry. 레이트리밋 [U]. `bank_tran_id`의 반복 조회 간 안정성(=dedupe 키 신뢰성) [U] → 실무 dedupe는 (일자+시간+입출구분+금액+잔액+적요) 조합.
- **법인/사업자 계좌 (핵심)**: 2025-01-02부터 법인 이용자도 오픈뱅킹 가능(금융위 2024-12-30 발표). 초기 14개 은행, **범위=잔액·거래내역 조회뿐(이체 제외)**, 수협/조합권 추후 확대 [V](https://www.fsc.go.kr/no010101/83750 등). 개인사업자 사업용 통장은 개인 명의 → 이미 개인 오픈뱅킹 커버. **은행별·계좌유형별 실제 커버는 계약 시 개별 확인** [U].
- 참가기관에 KB·신한·우리·하나·NH·IBK·카카오뱅크·토스뱅크 8행 모두 포함 [V](https://openapi.kftc.or.kr/service/openBanking).
- 참여 요건: 개발은 sandbox 자유이나 **운영은 전자금융업 등록 + 금결원 오픈뱅킹공동업무 이용계약** 필요 [V/partial]. 심사 기간·비용: 벤더 주장 "수백만원/월 + 수주" [C](https://bankapi.co.kr/blog/bankapi-vs-kftc-comparison).

#### B. CODEF (※ 코드에프(주) 운영 민간 데이터 브로커 — "금융결제원" 아님, 일반적 오칭 정정)
- 계좌빠른조회/거래내역 개인·법인 상품 실재 [V-존재](https://developer.codef.io), 지원은행矩阵/OTP 거동은 [U](06번 문서에서 심층).
- 동종 구조(고객이 공동인증서 등록 → 주기 수집)의 공개 문서: **팝빌 easyfinbank 20개 은행 개인+법인** — "공동인증서를 통해 인터넷뱅킹 로그인", 씨티/케이뱅크는 법인계좌만 [V](https://developers.popbill.com/guide/easyfinbank/introduction/regist-bank-account).

#### C. 은행 직접 (기업인터넷뱅킹/기업 OpenAPI)
| 은행 | 실재 확인된 것 | 판정 |
|---|---|---|
| KB국민 | **기업OpenAPI 포탈 obizapi.kbstar.com 실재** [V]. 조회서비스 카탈로그에: 다른은행 자유입출금 거래내역, 오픈뱅킹 계좌목록·거래내역, **카드 승인/청구내역, 카드매출/현금영수증/세금계산서 조회**까지 리스팅 [V](https://obizapi.kbstar.com/quics?page=C108082). 인가/자격 상세 [U] | 유일하게 확인된 은행 발행 기업 API. KB 기업고객이면 최우선 검토 |
| NH농협 | NH오픈플랫폼 잔액조회/**거래내역조회 API** (예금주실명확인, 핀-어카운트, 건별 과금) [V-존재](https://nhfintech.nonghyup.com/content/svcportal/html/nhAPI/AP_5010.html), 온보딩 요건 [U] | 시도 가치 |
| 신한 | api.shinhan.com / openapi.shinhan.com 포탈 존재 [V], 기업 거래내역 노출 여부 [U] | 미확정 |
| IBK | devapiportal.ibk.co.kr 존재 [V], 거래내역 [U] | 미확정 |
| 우리 | baas.wooribank.com(이음) 존재 [V], 내용 [U] | 미확정 |
| 하나 | Hana API On(2026-03 오픈) — 소비자 API 중심, 기업 거래내역 미확인 [C] | 미확정 |
| 카카오뱅크 | 기업 거래내역 API 없음. 계좌연결/인증 API는 본인확인용 [U]. 개인사업자통장·사업자인증서 상품 있음 [V] | 오픈뱅킹(개인) + 내역서 다운로드 |
| 토스뱅크 | 뱅크 측 오픈 API 없음, 거래내역서 발급은 앱/고객센터 [V-FAQ](https://support.toss.im/faq/5251) | 오픈뱅킹 참가기관 + 수동 |
- 레거시: 전 은행 기업인터넷뱅킹 엑셀/파일 다운로드 가능(공동인증서+OTP) [V-관행], BL/BAW 서식별 차이 [U].
- **"파야(PAYA)" 계좌지원 서비스: 검색에서 실체 없음** [U/사실상 부재]. 관련 KFTC 공개API는 **어카운트인포**(계좌목록/해지/이체, 거래내역 없음, 만19세 내국인 개인 한정) [V](https://openapi.kftc.or.kr/service/accountInfo).

### 1.2 은행별 권고 (A4 매트릭스 요약)
- **전 은행 공통 권고 경로**: ① KFTC 오픈뱅킹(전자금융사업자 인가 후, 장기) ② CODEF/팝빌급 집중기관 경유(법인 계좌, 인증서 등록 파이프) ③ 사용자 CSV/거래내역서 업로드 폴백(무조건 합법).
- KB=기업OpenAPI 직접(단 KB 기업고객), NH=오픈플랫폼 시도, 나머지는 오픈뱅킹+집중기관.
- **stable unique txn id**: bank_tran_id 필드는 존재 [V]하나 재조회 안정성 [U] → 수집 raw에 저장 후 retrospective 검증 필요.
- 거래처명 파싱 계층(적요→counterparty normalization)이 어떤 경로든 필수.

### 1.3 법적 레드라인 (설계 제약 확정)
- **대법원 2020도9972** [V](https://casenote.kr/대법원/2020도9972): 접근매체(ID/PW·공동인증서·OTP 토큰)를 타인 명의 거래에 이용되게 수집·보관 = **전자금융거래법 위반**.
- → **EASY는 고객 자격증명(ID/PW, 인증서 파일, OTP)을 절대 보관하지 않는다.** 합법 경로는 3개뿐: (a) 자체 인가(오픈뱅킹/마이데이터) (b) 인가된 집중기관(CODEF/쿠콘/팝빌/하이픈/바로빌) 경유 — 동의·연결 계약은 벤더가 보유 (c) 사용자 본인 다운로드 후 업로드.
- 마이데이터는 2022-08 스크레이핑 금지·API 전환 [V]. 기존 집중기관(토스/뱅크샐러드) 데이터 재양도를 받는 방식은 신용정보법상 재전송 제한으로 불가능 추정 [U-고위험].

## 2. 사업용 신용카드 지출

### 2.1 카드사 공식 API (보유자용)
- **BC카드 Open API**: 신규회원 등록/카드목록/배송조회만 — **이용·승인·청구내역 없음** [V](https://www.bccard.com/OpenAPI/index.html).
- **신한카드 DataBada**: 승인/이용데이터 API 상품 존재 [V-존재](https://databada.shinhancard.com/serv/apis/prod/srch), 대상·가격 [U].
- 삼성/KB/현대/롯데/하나/농협: 보유자용 거래내역 공개 API **미발견** [U→사실상 없음].
- 결론: **카드사 직접 연동로는 성립하지 않는다.**

### 2.2 집중기관 경유
- CODEF 카드: **개인/법인 승인내역, 청구내역, 카드목록** 상품 실재 [V-존재](https://developer.codef.io/products/card/common/b/approval). 이용(승인)내역=승인 시점 건별(dedupe 키=승인번호) vs 청구내역=월 청구 라인(할부 포함) 구분 중요.
- 벤더 비교 [C](https://faqnfaq.com/blog/corporate-card-api-compare/): CODEF "건당 ~10원+월 최소 ~80만원", 바로빌 "카드당 월 3,000원", 하이픈 "월 10만원대" — **전부 마케팅 주장, 계약견적으로 재확인 필수**.
- 바로빌 카드조회: 9개 카드사, 필드 카드사/마스킹카드번호/승인금액/**승인번호**/거래일시/가맹점명/**가맹점사업자번호**, "공인인증서 없이 ID/PW" [C](https://m.blog.naver.com/knetbiz/223803501674) — ID/PW 모델은 §1.3 리스크를 벤더가 떠안는 구조이므로 **벤더 계약서상 동의·보관 책임을 확인**해야 함.

### 2.3 마이데이터 경유
- 카드 업권 표준API 실재 [V](https://developers.mydatakorea.org/mdtb/apg/mac/bas/FSAG0406?id=2): **국내/해외 승인내역 Card-008/009**(`approved_num`, `merchant_name`, `approved_amt`, `approved_dtime`), **카드매입정보 Card-014/015**(`purchase_num/purchase_date`), 청구추가정보 Card-005. `approved_num` = 안정적 dedupe 키.
- 제약: 요청자는 마이데이터 **허가 사업자** 한정, 정보주체는 개인 본인 → **법인(사업자)카드**는 카드 소유자 개인의 동의 파이프 아니면 불가.

### 2.4 Always-legal 폴백 + 선례
- 카드사 홈페이지 이용내역 엑셀/CSV 다운로드(국민/신한/하나/현대/삼성 등)는 세무 실무 표준 [V-guides] → 사용자 업로드 = 자격증명 리스크 0.
- **경쟁사 이지샵**이 인증서/ID·PW 기반 카드·은행 연결 + 개인/기업뱅킹 티어 선택을 운영 중 [C](https://www.easyshop.co.kr/taxCustCntr/taxCustCntrGuide/taxCustCntrSt.kicc) — incumbent도 인증서 연결 모델을 쓴다는 시장 증거(단 법적 평가는 별개).

## 3. PG (후순위 확장 — 구조만)

| PG | 결제/취소 | 정산/수수료 | 근거 |
|---|---|---|---|
| 토스페이먼츠 | Core API [V] | **정산 API 실재**: `pay.toss.im/api/v2/settlement-details` (총액·서비스수수료·부가세 combo, apiKey, 실결제만) [V](https://docs-pay.toss.im/reference/settlement/detail). 입금계좌/paidOutDate 필드 [U] | 가장 준비됨 |
| 카카오페이 | 주문/결제 조회+취소 [V] | 정산내역 **파일(.json) 다운로드 서비스** 공식 포럼 확인 [V](https://developers.kakaopay.com/forum/t/api/580), 엔드포인트 문서 [U] | 파일 파싱형 |
| NHN KCP | 거래조회 API (승인/취소 상태) [V-존재](https://developer.kcp.co.kr/reference/search) | 수수료/정산 [U] | |
| KG이니시스 | 거래조회 v1(NVP)/v2(JSON), SHA-512 서명 [V](https://manual.inicis.com/pay/etc-inquiry.html) | 조회 응답에 수수료·정산 필드 없음 [V] → 정산은 관리자센터/파일 대사 [V-존재] | |
| 네이버페이 | 결제형/주문형 승인·취소 [V] | 공개 정산 API 미발견 [U] → 관리자센터 export + 주문형 payment-keyword 대사 | |
- **PortOne**가 5개 PG 결제/조회/정산을 정규화 — 5중 계약 대안 [V-존재](https://developers.portone.io/opi/ko/integration/pg/v2/naverpay?v=v2).
- 설계 반영: PG 어댑터는 `payment(승인) → cancel → fee → settlement → payout` 5단계 관계 모델을 미리 확보(→14번 cross-source 문서).

## 4. EASY 수집 계층 시사점 (종합)
1. 은행 필드 매핑: `tran_date/tran_time/inout_type/tran_amt/after_balance_amt/print_content` (+`bank_tran_id` raw 보존) → 표준 스키마. 거래처는 적요 파싱 + counterparty 매핑 레이어 필요.
2. 카드 dedupe 1차 키 = **승인번호**(마이데이터 approved_num, CODEF resCardApprovalNo), 2차 = (카드사+사용일시+가맹점+금액).
3. 자격증명 금지 원칙이 아키텍처를 결정: **집중기관 토큰(connectedId) 또는 사용자 업로드만 저장 대상으로 허용** → 15번 보안 문서.
4. 구현 순서상 은행·카드 자동수집은 CODEF/팝빌 계약(06번) 또는 마이데이터 인가(장기)에 선행 조건이 걸림 → Phase 로드맵에서 파일 반자동(이지샵 라인 재사용)이 선행-safe 기본값.
