# 스마트스토어 전자책 주문 자동화

상품 `12879870964`의 주문 변경을 5분마다 조회해 소유자 전용 SQLite 대기열에 기록한다. Commerce API는 토큰 발급과 주문 조회만 사용하며 발송처리/취소/환불 API는 호출하지 않는다. 기존 앱 서버·RDS와 독립된 운영 작업이며 기존 데이터/미디어를 수정하지 않는다.

## 처리

- 활성화 기준 이전 결제와 이미 발송/구매확정된 주문은 자동 발송하지 않는다.
- PAYED, 클레임 없음, 결제시각 확인, 배송메모 이메일 정확히 하나인 신규 주문만 READY다.
- 중복 조회는 주문번호 고유키로 갱신한다. SMTP_ACCEPTED/UNKNOWN 상태는 재발송하지 않는다.
- SMTP 중 연결이 끊기거나 프로세스가 종료되면 UNKNOWN으로 두고 수동 확인한다. SMTP_ACCEPTED는 실제 수신 확인을 뜻하지 않는다.
- 상세 원문·주소·전화는 저장하지 않는다. 배송용 이메일·등업 검토용 구매자 이름만0700 디렉터리/0600 파일에 보관한다. 로그는 상태별 건수만 출력한다.
- 카페 등업은 REVIEW 대기열만 만든다. 이름만으로 자동 매칭하거나 권한을 변경하지 않는다. 카페 ID/닉네임과 구매자 일치 확인 후 처리할 운영 경로는 별도 연결한다.

## 실행

`python3 -m unittest discover -s scripts/ebook -p 'test_*.py'`

서버 `/etc/f2r-ebook/commerce.json`은 기존 client_id/client_secret/product_id와 함께 `activation_from`(타임존 포함 ISO8601)을 사용한다. `live_send=false`가 기본이다. 상태 경로는 `/var/lib/f2r-ebook`, 프로그램 경로는 `/opt/f2r-ebook`이다. 외부 Python 패키지 없이 Python3.9 표준 라이브러리와 libcrypt bcrypt 지원을 사용한다.

실발송에는 검증된 `pdf_path`, `pdf_sha256`, `body_path`, `subject`, `smtp_config`가 필요하다. SMTP 파일은 host/port(465)/username/password를 포함하며0600으로 보관한다. 비밀값은 저장소/로그에 남기지 않는다. 기존 계정의 앱 비밀번호는 사용자가 직접 입력한다. 원본 PDF의 해시가 달라지면 발송하지 않는다.

## 현재 범위

주문 수집 자동화와 발송기 구현. PDF·발송 본문·SMTP 연결 및 운영자 테스트 수신 검증을 완료하기 전에는 실발송을 켜지 않는다. 구매자에게 허위로 등업 완료를 알리지 않도록, 본문은 실제 등업 상태와 맞춰 확정한다. 대기열의 업데이트는 주문 관측과 발송 상태 기록에 한정하며 삭제 기능은 제공하지 않는다(감사·중복 방지 목적).

공식 API 계약: https://apicenter.commerce.naver.com/docs/commerce-api/current/seller-get-last-changed-status-pay-order-seller
