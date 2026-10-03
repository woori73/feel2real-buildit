# NewStart Phase 1 — 개인화 상담 중심 전환 (2026-10-03)

## 정책
- 1:1 비공개 상담: **로그인한 모든 회원** (이전: THANKS_MEMBER). 소유권(`member_user_id`) HARD GATE는 그대로.
- 지피지기 Agent / 금감원 민원 실행: **THANKS_MEMBER + ADMIN** 그대로 (변경 없음).
- A to Z 화면(`/newstart/a-to-z`): **ADMIN 전용**. 비ADMIN은 `/newstart`로 redirect. `atoz_step`·`AtozCase`·상담 자동분류·요청 단계 분류 데이터는 그대로.
- HOME: 히어로 → 1:1 상담 CTA → 내 채무상황(요약) → 지피지기(감사회원만 입력창, 그 외 안내 카드) → 필요한 도움 → 공시 업데이트.

## 새 상담 → 운영자 이메일
- `NewStartConsultationNotifier` — 상담 **커밋 이후**(afterCommit) 비동기 발송. 실패/미설정이어도 상담 저장 영향 없음.
- 메일: 상담번호·닉네임·접수시각·미리보기(30자, 6자리 이상 숫자 마스킹)·관리자 상담함 링크. 본문 전문/채무정보/이메일/로그인ID 미포함.
- 설정은 감사회원 예외 알림과 공유(`app.newstart.thanks.*`): `NEWSTART_SMTP_HOST/PORT/USER/PASSWORD`, `NEWSTART_ALERT_FROM`, `NEWSTART_THANKS_ALERT_TO`(기본 woorinaesin@naver.com), 선택 `NEWSTART_CONSULTATION_ADMIN_URL`.
- SMTP 환경변수가 비어 있으면 발송을 건너뛰고 WARN 로그만 남긴다.

## DB
- migration 없음.
