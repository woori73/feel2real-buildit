# 금융·세무 자료 수집 지원 가능성 — 작업 B

조사일: 2026-10-22. 기준: `main` `e7247e831c3b82a67e880621d041759067125360` (PR #851).
제출 전 최신 `main` `07d9316a293a30a84e13a09c42957bd64be71bd8` (PR #852)을 반영했다. 추가 변경은 입시 진단 스크립트로 EASY 조사 대상 코드는 동일하다.
공개 공식 문서만 조사했다. 금융기관 계정 로그인·계약 신청·실거래 수집은 실행하지 않았다.

## 기관별 판정

| 기관 / 자료 | 확인한 공식 경로 | 아직 필요한 조건 | 이번 프로토타입 |
|---|---|---|---|
| 여신금융협회 / 카드 승인·매입·입금 | [가맹점 매출거래정보 통합조회](https://www.cardsales.or.kr/)가 승인·매입·입금·매출장부 메뉴를 제공한다. 일반 개발자용 전체 내역 조회 API는 공개 자료에서 확인하지 못했다. | 사업자의 동의 외에 협회의 영업 이용 사전 승낙, 제휴·자동화 허용 범위 확인이 필요하다. 화면의 존재는 Playwright 허가를 의미하지 않는다. | `CARD_SALES`, `CARD_SETTLEMENT` 파일 초안. 실제 사이트 다운로드 OFF. 매출과 정산은 서로 다른 경로로 표시한다. |
| 카드사 / 사업용 사용내역 | [우리카드 Developers](https://apiportal.wooricard.com/)에 승인·청구 조회와 [제휴 절차](https://apiportal.wooricard.com/partnership)가 있다. [BC Open API FAQ](https://www.bccard.com/OpenAPI/faq.html)는 제휴 협의를 안내한다. | 회사별 서비스·개인사업자/법인 대상·필드·동의·이용계약을 각각 확인해야 한다. 카드사 전체를 하나의 공개 API로 간주하지 않는다. | `CARD_PURCHASE` 파일 초안. 기존 신한카드 XLS 파서·카드 이벤트 승인 서비스를 재사용 후보로 조사했다. 새 일반 confirm 경로로 카드 비용을 보내지 않는다. |
| 은행 / 사업용 입출금 | 금융결제원 [거래내역조회](https://openapi.kftc.or.kr/openapi/openbanking/transactionInquiry)와 [사용자인증](https://openapi.kftc.or.kr/openapi/openbanking/userAuth). 개인·법인 조회 경로가 구분된다. | [회원가입·API 신청·계약](https://openapi.kftc.or.kr/intro/useProcedure), 해당 사업자·계좌의 지원 여부, 운영 사용 승인, 전체 페이지 회수·동의 범위가 필요하다. | `BANK_ACCOUNT` 파일 초안 → 기존 staging → 명시 승인 → 기존 장부 API. API 실호출은 구현·실행하지 않았다. |
| 홈택스 / 전자세금계산서·현금영수증 | [홈택스](https://www.hometax.go.kr/)에 본인 자료 조회 메뉴가 있다. 국세청은 [홈택스/등록 ASP/ERP의 전자계산서 발급 방법](https://j.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=7788&mi=2462)을 안내한다. | 발급·전송 연동과 기존 전체 증빙 조회는 별도 업무다. 범용 본인 증빙 일괄조회 공식 API와 브라우저 자동화 허용을 공개 자료만으로 확정하지 못했다. | `TAX_INVOICE`, `CASH_RECEIPT` 파일 증빙 초안. 원거래와 매칭하기 전 일반 수입·지출 생성 차단. 실제 홈택스 접속 OFF. |

우리카드의 [2026-09-14 공지](https://apiportal.wooricard.com/portalnotice/4)는 **신규 제휴 기관의 법인카드 이용내역 API 연동 중단**, 기존 제휴 유지라고 명시한다. API 소개 페이지가 있어도 신규 사용이 가능하다고 안내해서는 안 된다.

협회의 [공개 회원가입 약관](https://www.cardsales.or.kr/page/member/join/joinForm) 제11조 제4·5항은 영업활동에 사전 승낙, 이용권한 양도에 명시적 동의를 요구한다. 이번 작업은 어떠한 제휴·자동화 승낙도 확보하지 않았다.

[국세청 사업자등록정보 진위확인·상태조회 API](https://www.data.go.kr/data/15081808/openapi.do)는 사업자 정보 확인용이다. 전자세금계산서·현금영수증 거래를 내려받는 API의 근거로 사용할 수 없다. 민간 스크래핑 상품도 기관 공식 허용의 증거로 취급하지 않았다.

## 실제 연동을 추가할 때의 인증 흐름

1. 대상 기관·사업체·계좌의 불투명 참조 ID·자료 종류·기간·목적을 사용자에게 표시한다.
2. 공식 API는 기관이 제공하는 인증·동의 페이지에서 사용자가 직접 처리한다. 향후 OAuth 연결은 state/만료/redirect URI 검증, 일회성 코드, 최소 권한이 필요하다. 이번 PR에는 OAuth 완료나 토큰 교환 코드가 없다.
3. 공식 API가 없으면 해당 사이트의 최신 약관과 서면 허용 범위를 먼저 확인한다. 사용자 동의만으로 기관의 제한이 해제되지는 않는다.
4. 허용된 브라우저 수집은 사용자의 PC에서 새 headed context를 연다. 로그인·CAPTCHA·OTP·공동인증서는 사람이 직접 처리하고, 완료 후 별도로 기간·대상을 확인해 수집을 승인한다.
5. 로그인 성공 감지는 인증 입력값을 읽지 않는 완료 신호만 사용한다. 추가 인증·세션 만료·접속 제한·화면 변경 시 수집을 중단하고 사용자 재인증 또는 수동 파일 방식으로 전환한다.
6. context/쿠키/storageState/프로필을 내보내거나 다음 작업에 재사용하지 않는다. 인증정보를 서버·AI 모델·로그에 전송하지 않는다. 완료·실패·승인 거부 모두 context를 닫는다.

현재 실행 가능한 브라우저 어댑터는 **`CARD_COMPANY_TEST` + 명시 포트의 loopback HTTP**뿐이다. 외부 이동·리다이렉트·하위 리소스·WebSocket을 차단한다. 실기관을 열기 위한 환경변수 스위치는 추가하지 않았다.

## 파일 대체 흐름과 지원 범위

사용자가 기관의 공식 화면에서 직접 인증하고 자료를 내려받은 뒤 업로드를 승인한다. 원본은 암호화 보존하고, 검증된 열 매핑의 정규화 템플릿에서 초안을 만든다. 원본이 미지원 양식이면 실패 보고와 함께 남기고 수동 검토/변환을 안내한다.

- CSV: UTF-8/BOM 또는 CP949, 명시 헤더, 정수 원화, 엄격한 날짜·따옴표 검사.
- Excel: 단일 시트 XLS/XLSX, 명시 헤더, 수식 거부, 평가/매크로 실행 없음.
- PDF: 텍스트가 있는 줄 단위 `|` 구분 템플릿만 지원한다. 스캔 PDF·기관별 임의 레이아웃·암호 입력을 요구하는 파일은 자동 추정하지 않는다.
- 취소·정정·할부·카드미지급금·정산·세무 증빙의 실제 회계 이벤트 연결은 별도 기관 매핑 검증이 필요하다. 파일 확장자 파싱 성공을 기관 양식 지원 완료로 표시하지 않는다.

## 최신 main 구조 조사

경로는 `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/easy/` 기준이다. 기존 9월 문서의 일부 설명은 최신 코드와 다르므로 아래 구현을 우선했다.

| 단계 | 확인한 구현 / 재사용 경계 |
|---|---|
| 파일 수집 | `EasyCollectionController.ingestCsv`, `EasyCollectionIngestService.ingestCsv` → CSV 파싱 → run/raw → `EasyImportService.stage`. 별도로 `ShinhanCardImportService`의 XLS/카드 처리 경로가 있다. |
| 공식 공급자 | `CrefiaCardsalesAdapter`는 실제 endpoint와 회계 lifecycle 승인 전 fail-closed. 클라이언트/credential provider가 존재해도 운영 수집 가능 판정이 아니다. 새 API는 이 인증 경로를 호출하지 않는다. |
| 브라우저 | `local-collector`는 새 visible context와 사람의 인증을 사용하지만 기존 구현은 로그인 후 별도 수집 승인·암호화 보관·외부 origin 제한이 부족했다. 이번 PR에서 mock 경로를 강화했다. 별도 legacy `easy-collection-worker`는 이번 live 경로로 사용하지 않는다. |
| 중복 | source/business/connection/external ID, 또는 tuple+파일 내 발생 순번, 장부 fingerprint. 새 경로는 dataset+accountRef 해시로 tuple까지 범위를 분리하고 외부 승인번호에 날짜·방향을 포함한다. 원문 충돌은 자동 덮어쓰기하지 않는다. |
| 분류 | `EasyImportService.stage`의 기존 계정 매핑·규칙 및 `EasyCardClassificationRecommendationService`의 명시 매핑/과거 이력/가맹점/규칙. 새 세무 규칙이나 세율을 추정하지 않는다. |
| 장부·분개 | `EasyImportService.confirm` → `EasyTransaction` → `EasyJournalService`. 실질 복식은 `EasyRealPostingService`, 사업용 카드는 `EasyBusinessCardAccountingService`, 카드매출·정산은 collection lifecycle 경로다. |
| 승인·재시도 | `EasyCollectionApprovalService`: PENDING→APPROVED→COMMITTED/COMMIT_FAILED. 승인 행 잠금과 장부 반영+완료상태의 DB 트랜잭션을 재사용한다. |
| 감사·보고 | collection run/raw/approval, collector task, correction log, review queue, Workspace 집계. 새 vault의 승인·업로드·staging·장부 반영 이벤트를 연결한다. |

기존 장부 fingerprint에는 실제 계좌 참조가 포함되지 않는다. 서로 다른 계좌의 우연히 같은 거래가 장부 단계에서 중복 의심/제외될 수 있어 raw와 배치 수를 반드시 대사해야 한다. 이 PR은 기존 장부 전체의 중복 정책을 변경하지 않는다.

저장소 운영 원칙의 즉시 운영 저장 규칙과 이번 요청의 승인 전 반영 금지는 충돌한다. **이번 사용자의 명시 지시를 우선**해 운영 저장을 하지 않고, 초안과 명시 승인 경계를 유지한다. 원칙 문서 자체는 수정하지 않았다.
