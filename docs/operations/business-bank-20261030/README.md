# 사업자 정보·무통장입금 안내 반영 (2026-10-30)

2026-10-28 사용자 확정 사업자 정보와 무통장입금 계좌를 홈페이지에 반영한다. 기존 신청 기능·복습 게임·홍보 영상·AI AGENT 트레일러는 그대로다. DB·마이그레이션 변경 없음.

## 반영 값

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 상호 | 압구정에듀홀릭출판사 | 사용자 지시(10-28 확정) |
| 대표자 | 박정민 | 사용자 지시 |
| 사업자등록번호 | 146-99-01347 | 사용자 지시, 기존 `HomeExamSellerAdvice`(PR #920) |
| 통신판매업 신고번호 | 제2026-서울송파-1411호 | 사용자 지시 |
| 주소 | 서울특별시 송파구 중대로10길 37-25, 4층(가락동) | 10-28 사용자 제공 출판사 정보(`HomeExamSellerAdvice`) + 10-10 이전 footer의 출판사 표기(커밋 `21f7f973` 이전)와 일치. 신고번호의 관할(송파)과도 일치 |
| 전화 | 010-8507-1453 | 10-10 이전 footer의 출판사 표기와 문의 모달(커밋 `21f7f973` 이전). 학원 번호 02-6324-1453은 쓰지 않음 |
| 이메일 | woorinaesin@naver.com | 사용자 지시 |
| 사업자 정보 확인 | https://www.ftc.go.kr/bizCommPop.do?wrkr_no=1469901347 | 사업자등록번호 기준 공정위 조회 |
| 무통장입금 | 신한은행 110-498-250866, 예금주 박정민, 20,000원, 2주·주 2회·총 4회·160문항 | 사용자 지시 |

출판사 등록증·신고증 원본은 이 작업 세션에서 직접 열람하지 못했다. 공정위 조회 페이지도 세션 네트워크에서 열리지 않아, 운영 배포 후 「사업자 정보 확인」 링크로 사용자가 최종 대조한다.

## 구조

- 사업자 정보는 `fragments/_public_footer.html`의 `sellerRows` 한 조각에만 둔다. 공통 footer, 시험 문제지 페이지(`/exam`), FAQ, 신청 화면, 정책 문서(`/policies`, `/policies/*`)가 모두 이 조각을 쓴다. 학원 정보(압구정에듀홀릭학원·759-91-01977·압구정로34길·02-6324-1453)는 Feel2Real 공개 화면에서 뺐다. 학원 페이지(BuildIt 학원 홈페이지 빌더·LMS) 데이터는 건드리지 않았다.
- 판매자 공개 여부 스위치 `app.home.exam-seller-confirmed`와 `HomeExamSellerAdvice`는 확정 정보로 대체되어 삭제했다.
- 무통장입금 계좌는 `HomeBankTransfer.CURRENT` 한 곳에 두고, 홈페이지 공개는 `app.home.bank-transfer-public` 하나로만 정한다(기본 false, `application-prd.yaml`에서 승인된 값 true, 끄려면 EC2 env `APP_HOME_BANK_TRANSFER_PUBLIC=false`). 맞춤 문제 신청 기능의 `app.exam-intake.enabled`·`bank-public-enabled`·관리자 계좌 등록과는 독립적이며 이번에 바꾸지 않았다. 입금 확인은 관리자 수동 확인이다.
- `/privacy`(NewStart Chrome 확장 안내)의 운영자 표기도 같은 화면 footer와 맞도록 출판사로 바꿨다.

## 남은 일(별도)

- 개인정보처리방침·이용약관·환불정책 본문의 「검토안·최종 승인 전」 표시는 이번 범위(사업자 표시)에서 그대로 두었다. 정식 문서로 바꾸려면 개인정보 보호책임자, 보유 기간, 처리위탁 업체, 시행일 확정이 필요하다.

## 화면(로컬, 계좌 공개 켬)

[휴대폰 결제 안내](screens/local-m390-payment.png) · [휴대폰 footer](screens/local-m390-footer.png) · [휴대폰 사업자 정보](screens/local-m390-business.png) · [휴대폰 시험 문제지 페이지](screens/local-m390-exam.png) · [PC 결제 안내](screens/local-pc1440-payment.png) · [PC footer](screens/local-pc1440-footer.png)
