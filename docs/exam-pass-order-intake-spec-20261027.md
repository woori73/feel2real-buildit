# 기말고사 이용권 주문·자료 제출 구현 명세 (2026-10-27)

## 기준 및 범위
- 기준 브랜치: feat/homepage-revamp-20261027 (PR #910). 기존 운영 자료, AI 비서 콘솔, 관리자 기능 보존.
- 가격: 20,000원. 3주/주 2회/총 6회/회당 40문항 기준 240문항은 실제 공급 가능 여부와 약관을 운영자가 확정하기 전까지 결제 가능한 확정 상품으로 게시하지 않는다.
- 대상: 학원장, 강사, 학부모, 학생. 미성년자 신청은 법정대리인 동의와 결제 주체 확인 필요.
- 결제 연동/운영 배포/광고비 집행은 별도 승인. 승인 전 상태는 '신청 접수'이며 '결제 완료'가 아니다.

## 고객 흐름
1. 랜딩 상단의 AI 비서 콘솔 유지, 바로 아래 이용권 20,000원 및 '신청하기' CTA.
2. /exam-pass/order 신청 화면: 신청자 유형, 성명, 연락처, 이메일, 학교, 학년, 시험일, 출판사/교과서, 시험 범위, 교사 힌트, 요청사항, 개인정보 수집·이용 동의, 미성년자 보호자 확인.
3. 자료 업로드: PDF/JPG/PNG/DOCX, 파일당 20MB·최대 10개(운영 정책 확정 필요). 클라이언트와 서버 양쪽에서 확장자·MIME·크기 검사, 악성파일 스캔, 안전한 비공개 저장소, 다운로드 접근 통제. 민감한 학생 개인정보가 포함된 파일 업로드 자제 안내.
4. 제출 완료: 서버가 생성한 접수번호, 신청 상태 '접수', 안내 메시지. 재제출/수정은 접수번호 및 본인 인증으로만 허용.
5. 관리자: 접수 목록, 자료 열람, 범위 변경 이력, 처리 담당자, 진행 상태, 승인/반려 사유, 안내 이력. 타 학원·타 고객 데이터 접근 금지.
6. 결제: PG 계약·환불 정책·주문 약관 확정 후 별도 구현. 결제 금액은 서버의 상품 스냅샷에서 결정하고 클라이언트 금액을 신뢰하지 않음.

## API 제안 (기존 인증/라우팅 관례 확인 후 확정)
- POST /api/exam-pass/orders: 신청 생성; 인증 사용자 또는 검증된 비회원 접수, rate limit/CAPTCHA.
- POST /api/exam-pass/orders/{id}/attachments: 인증된 업로드 URL 발급/업로드 확정.
- GET /api/exam-pass/orders/{id}: 신청자 본인 또는 권한 있는 관리자만 조회.
- GET /api/admin/exam-pass/orders: 관리자 목록, 학원 격리.
- PATCH /api/admin/exam-pass/orders/{id}/status: 감사 기록 포함 상태 변경.
- 상태: RECEIVED → REVIEWING → AWAITING_PAYMENT → PAID → IN_PROGRESS → DELIVERED / CANCELLED / REFUNDED. 결제 전 PAID 전환 금지.
- 파일과 개인정보 보존기간·삭제 정책, 개인정보처리방침 고지 개정, 운영자 열람 기록 필수.

## 데이터 구조 제안
exam_pass_order(id, applicant_type, applicant_user_id, applicant_name, phone_encrypted, email_encrypted, school, grade, exam_date, textbook, scope, teacher_tips, request_note, product_version, price_krw, status, consent_version, consent_at, created_at, updated_at)
exam_pass_attachment(id, order_id, storage_key, original_name, mime, size_bytes, scan_status, uploaded_at)
exam_pass_status_history(id, order_id, actor_id, old_status, new_status, reason, created_at)
exam_pass_payment(id, order_id, provider, provider_payment_id, amount_krw, state, paid_at) — PG 도입 이후.
비밀번호·토큰·개인정보를 분석 이벤트나 애플리케이션 로그에 기록하지 않음.

## 필수 테스트
- 4개 신청자 유형, 필수값 누락/이메일·전화번호 검증, 중복 제출 방지.
- 허용/차단 파일 형식·용량·개수, 경로 조작 및 비인가 다운로드 차단.
- 학생/학부모/강사/원장/관리자별 주문 조회 권한, 학원 간 격리.
- 결제 전 주문을 결제 완료로 오인하지 않음; 가격 20,000원 서버 검증.
- 개인정보 동의 미체크 시 제출 불가; 감사 로그; 모바일 폼 및 접근성.
- 랜딩 AI 비서 콘솔 첫 화면 유지, 기존 JS 테스트·CI 통과.
- 테스트는 합성 개인정보만 사용; 실제 고객에게 자동 메시지 발송 금지.

## 구현 순서
1. PR #910의 랜딩 가격·CTA를 20,000원으로 수정하고 기존 콘솔 위치 유지.
2. 신규 주문 폼/백엔드/DB 마이그레이션/파일 저장 및 권한 구현.
3. 관리자 주문 처리 화면 및 테스트.
4. 스테이징에서 모바일/PC 실사용 시나리오 검증.
5. 약관·환불·PG 승인 후 결제 구현, 운영 배포 별도 승인.
