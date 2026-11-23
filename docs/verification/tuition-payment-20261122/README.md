# 수강료 청구 링크·입금 대조(시험 모드) 화면과 검증 — 2026-11-22

로컬(`local` 프로필, MySQL 8.4 컨테이너)에서 `F2R_PAYMENT_ENABLED=true`, `F2R_PAYMENT_CARD_TEST=true`로 띄워 찍었다. 학생 이름·계좌·입금자는 모두 지어낸 것이다. 운영 서버·운영 DB는 건드리지 않았다.

| 파일 | 화면 |
|---|---|
| `01-admin-links.png` | 원장 화면 `/easy/billing` — 청구 링크 3개를 만든 뒤 |
| `02-admin-after-match.png` | 대조 실행 뒤 — 입금 3건 중 1건 납부 기록, 2건 확인 필요(금액만 같음 / 번호는 맞지만 금액 다름) |
| `03-admin-mobile.png` | 같은 화면, 휴대폰 폭(390px) |
| `04-parent-bill-unpaid-mobile.png` | 학부모 청구 화면(납부 전), 휴대폰 폭 |
| `05-parent-bill-paid-mobile.png` | 학부모 청구 화면(납부 확인 뒤) |
| `06-test-checkout-mobile.png` | 시험 결제창 |
| `07-result-fail-mobile.png` | 실패 안내 |
| `08-result-success-mobile.png` | 성공 안내 |

화면 확인 중 함께 본 것: 없는 토큰 주소는 404, 학부모 화면 HTML에 학생 실명이 없고 가린 이름만 있음.

테스트 결과는 PR 본문 "검증"에 적는다.
