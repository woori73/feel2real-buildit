# 11 — 기관 사이트 직접 받기 (MCP) : 유료 수집 API 없이 복식장부 운영

> 작성 2026-11-10. 대표님 지시: "CODEF 이용에 요금이 필요하면 MCP로 각 사이트 이동하여 로그인 후 자료 다운받아서 복식장부 운용하도록 설계 변경. 각 은행·카드사·홈택스·여신협회 링크 목록을 주어 MCP로 작업 가능하게."

## 1. 왜 바꾸나 — CODEF 비용

| 항목 | 확인 내용 (근거: 06·10 문서) |
|---|---|
| 무료 | 샌드박스·데모만 무료, 데모는 **하루 최대 100회** |
| 정식 사용 | 사업자·프로젝트 등록 → 운영팀 검토(약 3영업일) → **B2B 계약** |
| 단가 | 공개 요금표 없음 → **견적 필요**. 공식 문서로 확인된 것은 "거래내역 5,000건 단위 과금"뿐 (제3자 비교 "건당 ~10원 + 월 최소 80만원"은 미검증) |

따라서 **유료 계약 전까지는 이 경로(사이트 직접 받기)를 기본으로 쓴다.** CODEF 어댑터(10 문서)는 지운 것이 아니라 계약·검증 전까지 `UNVERIFIED`로 꺼져 있다.

## 2. 흐름

```
관리자(사람)                         AI (MCP 연결: Claude·ChatGPT)               F2R 서버
───────────                         ─────────────────────────                  ────────
                                    easy_collection_sites  ───────────────▶  공식 주소·순서·안전 규칙·사업체
공식 주소로 접속, 직접 로그인 ◀──── 주소 안내 (브라우저 사용 AI면 이동까지)
(인증서·OTP·비밀번호는 본인만)
거래내역 조회 → 엑셀 저장  ◀──────── 메뉴 안내, 조회 건수·합계 확인
                                    파일을 행으로 정리
                                    save_easy_site_rows  ─────────────────▶  ① 건수·합계·기간 검증(틀리면 저장 0)
                                                                              ② 원본 보존(raw, provider=MCP_SITE)
                                                                              ③ 원본 키 중복 방지
                                                                              ④ 장부 fingerprint 중복 제외
                                                                              ⑤ 중앙 장부 저장 + 분개
                                                                              ⑥ 자동분류 / 모르면 미분류·검토
결과 확인 (/easy/connections 수집 이력, /easy/reviews 검토함) ◀────────────  run 결과(저장·중복·검토 건수)
```

- 서버는 기관 사이트에 **접속하지 않는다.** 로그인·파일 저장은 관리자의 PC·브라우저에서 일어난다.
- 비밀번호·인증서 암호·OTP·계좌 전체 번호를 받는 칸이 없다. 행에 모르는 칸(password 등)을 넣으면 400으로 막는다.
- 이체·결제·해지 같은 쓰기 도구는 없다. 장부 삭제·승인 도구도 없다.

## 3. 복식장부와 이중 계상 방지

| 자료 (dataset) | 어디서 받나 | 장부 처리 |
|---|---|---|
| `BANK_ACCOUNT` 통장 입출금 | 은행 (기업·개인사업자 뱅킹) | 수입/지출 + 은행 판정. 카드대금 출금은 비용 아님(카드 미지급금 상환), 카드사 정산입금은 매출 아님(카드매출채권 회수), 정책자금 입금은 차입금, 지원금은 영업외 검토 — [ACADEMY_TAX_CHECKLIST](../ACADEMY_TAX_CHECKLIST.md) |
| `CARD_PURCHASE` 카드 사용 | 카드사 또는 홈택스(사업용 신용카드) — **한 곳에서만** | 비용, 증빙=신용카드. 취소는 원승인번호+원승인일이 있으면 원거래와 연결된 역분개 |
| `CARD_SALES` 카드 매출 | 여신금융협회 카드매출 조회 | 수입(카드매출), 증빙=신용카드. 매출 취소는 역분개 |
| 전자세금계산서·현금영수증 | 홈택스 | **장부에 올리지 않음**(은행 거래와 같은 돈이라 두 번 잡힘). 대조용으로만 보기 |

## 4. 기관 공식 주소 목록 (코드: `EasySiteCollectionCatalog`)

"응답 확인"은 2026-11-10 해외 클라우드 검증 환경에서 HTTPS 200을 받았는지다. "시간초과"는 해외 접속 차단으로 보이며, 국내에서는 정상일 수 있다(주소 자체는 각 기관 대표 주소).

| 구분 | 코드 | 기관 | 공식 주소 | 기업·사업자용 | 응답 확인 |
|---|---|---|---|---|---|
| 은행 | KB_BANK | KB국민은행 | https://www.kbstar.com/ | https://obiz.kbstar.com/ | 기업용 200 / 개인 시간초과 |
| 은행 | SHINHAN_BANK | 신한은행 | https://www.shinhan.com/ | https://bizbank.shinhan.com/ | 200 / 200 |
| 은행 | WOORI_BANK | 우리은행 | https://www.wooribank.com/ | https://nbi.wooribank.com/ | 200 / 200 |
| 은행 | HANA_BANK | 하나은행 | https://www.kebhana.com/ | https://biz.kebhana.com/ | 200 / 200 |
| 은행 | IBK_BANK | IBK기업은행 | https://www.ibk.co.kr/ | https://kiup.ibk.co.kr/ | 200 / 200 |
| 은행 | NH_BANK | NH농협은행 | https://banking.nonghyup.com/ | https://ibz.nonghyup.com/ | 200 / 200 |
| 은행 | SC_BANK | SC제일은행 | https://www.standardchartered.co.kr/ | - | 200 |
| 은행 | POST_BANK | 우체국예금 | https://www.epostbank.go.kr/ | - | 200 |
| 은행 | KFCC | MG새마을금고 | https://www.kfcc.co.kr/ | - | 시간초과 |
| 은행 | SUHYUP_BANK | Sh수협은행 | https://www.suhyup-bank.com/ | - | 시간초과 |
| 은행 | BUSAN_BANK | BNK부산은행 | https://www.busanbank.co.kr/ | - | 200 |
| 은행 | KN_BANK | BNK경남은행 | https://www.knbank.co.kr/ | - | 시간초과 |
| 은행 | IM_BANK | iM뱅크(옛 대구은행) | https://www.imbank.co.kr/ | - | 시간초과 |
| 은행 | KJ_BANK | 광주은행 | https://www.kjbank.com/ | - | 200 |
| 은행 | JB_BANK | 전북은행 | https://www.jbbank.co.kr/ | - | 200 |
| 은행 | K_BANK | 케이뱅크 (앱 중심) | https://www.kbanknow.com/ | - | 200 |
| 은행 | KAKAO_BANK | 카카오뱅크 (앱 중심) | https://www.kakaobank.com/ | - | 시간초과 |
| 은행 | TOSS_BANK | 토스뱅크 (앱 중심) | https://www.tossbank.com/ | - | 시간초과 |
| 카드 | SHINHAN_CARD | 신한카드 | https://www.shinhancard.com/ | - | 200 |
| 카드 | KB_CARD | KB국민카드 | https://card.kbcard.com/ | - | 200 |
| 카드 | SAMSUNG_CARD | 삼성카드 | https://www.samsungcard.com/ | - | 200 |
| 카드 | HYUNDAI_CARD | 현대카드 | https://www.hyundaicard.com/ | - | 시간초과 |
| 카드 | LOTTE_CARD | 롯데카드 | https://www.lottecard.co.kr/ | - | 200 |
| 카드 | WOORI_CARD | 우리카드 | https://www.wooricard.com/ | - | 시간초과 |
| 카드 | HANA_CARD | 하나카드 | https://www.hanacard.co.kr/ | - | 시간초과 |
| 카드 | BC_CARD | BC카드 | https://www.bccard.com/ | - | 시간초과 |
| 카드 | NH_CARD | NH농협카드 | https://card.nonghyup.com/ | - | 200 |
| 세무 | HOMETAX | 국세청 홈택스 | https://www.hometax.go.kr/ | - | 시간초과 |
| 카드매출 | CREFIA_CARDSALES | 여신금융협회 카드매출 조회 | https://www.cardsales.or.kr/ | - | 시간초과 |
| 수강료(링크만) | PAYSSAM | 결제선생 (수강료 청구·수납) | https://manager.payssam.kr/ | - | 200 |

메뉴 이름(예: "조회 → 거래내역조회 → 엑셀 저장")은 사이트 개편으로 바뀔 수 있어 범주별 안내 문구(`steps`)로만 둔다. 화면에서 "거래내역", "이용내역", "엑셀", "내려받기" 글자를 찾는다.

## 5. AI가 지키는 규칙 (`safetyRules`, 도구 응답에 매번 포함)

1. 로그인·인증서·비밀번호·OTP·보안카드·문자인증은 사용자 본인이 직접 입력한다. AI는 대신 입력하거나 묻지 않는다.
2. 비밀번호·인증서 암호·계좌/카드 전체 번호를 대화창·도구 인자에 적지 않는다.
3. 조회·내려받기만 한다. 이체·결제·해지·설정 변경 버튼은 누르지 않는다.
4. 목록의 공식 주소로만 접속한다(검색 광고·문자 링크 금지).
5. 추가 인증·보안 프로그램 설치·접속 제한 화면이 나오면 멈추고 사용자에게 넘긴다.
6. 사이트에 표시된 조회 건수·금액 합계를 `expectedCount`·`expectedTotal`로 함께 보낸다(합계는 입출금을 빼지 않고 모두 더한 값). 다르면 저장되지 않는다.

## 6. 쓰는 방법 (관리자)

1. ChatGPT 또는 Claude에서 F2R MCP를 연결할 때 동의 화면에서 **easy-collect:write**를 선택한다(민감 항목 경고 확인). 이미 연결돼 있으면 다시 연결해야 새 항목이 생긴다.
2. AI에게: "F2R 장부 사이트 목록 보여줘 → 국민은행 기업뱅킹 10월 거래내역 받아서 학원 장부에 저장해줘".
3. AI가 주소를 알려주면(브라우저를 다루는 AI면 그 주소로 이동) 직접 로그인한다.
4. AI가 조회·엑셀 저장을 안내하고, 파일 내용을 정리해 `save_easy_site_rows`로 저장한다. 결과의 저장·중복·검토 건수를 확인한다.
5. `/easy/connections` 수집 이력과 `/easy/reviews` 검토함에서 미분류를 정리한다.

## 7. 한계·확인 필요

- 브라우저를 직접 다루는 AI 기능(예: Claude in Chrome, ChatGPT 에이전트)은 금융 사이트에서 동작을 제한하거나 보안 프로그램 때문에 막힐 수 있다. 막히면 사람이 파일을 내려받고, AI는 파일 정리·저장만 맡는다.
- 각 기관 이용약관의 자동화 이용 조항은 기관마다 다르다(여신금융협회 약관 제11조: 영업 목적 이용은 사전 승낙). 이 설계는 **사업자 본인이 본인 자료를 직접 로그인해 내려받는 것**을 전제로 하며, 서버 자동 로그인·대량 스크래핑은 하지 않는다.
- AI가 옮겨 적는 과정의 오류는 건수·합계 검증으로 막지만, 금액이 서로 상쇄되는 두 실수(한 행 +1,000원, 다른 행 -1,000원)는 합계로 잡히지 않는다. 미분류·검토함에서 사람이 최종 확인한다.
- 원본 엑셀 파일 자체는 서버에 보관하지 않는다(보존되는 원본은 AI가 보낸 행 JSON). 파일 원본 보관이 필요하면 다음 단계로 Studio 저장소 첨부를 검토한다.

## 8. 결제선생 — 링크만 연결 (2026-11-10)

대표님 결정: 결제선생은 **링크만** 연결한다. 결제선생 수납 자료는 장부에 올리지 않고(`datasets` 비어 있음 → 저장 거절), 수강료는 통장에 들어온 정산 입금(페이민트 등)으로 장부에 잡힌다. 공식 API는 파트너 제휴 계약·검수·운영 키가 필요하고 기간별 수납 목록 조회가 없어 쓰지 않는다.
