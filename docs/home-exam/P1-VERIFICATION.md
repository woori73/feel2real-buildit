# 신청·자료·입금 관리 검증 — 2026-10-28 KST

PR #919 위에 쌓은 별도 P1 브랜치. 기본 비활성이고 운영 DB/미디어/배포를 변경하지 않았다. [설계](P1-DESIGN.md), [제안 스키마](proposed-schema.sql), [샘플/법률 검토](REVIEW-GATES.md).

## 구현 및 검증 범위

| 항목 | 로컬 결과 | 근거/한계 |
|---|---|---|
| 2주·주2회·4회·40문항·160문항·20,000원 | PASS | 서버 상품 스냅샷, 과거 21일/240문항 계약 보존 검사 |
| 문의·신청 검증/멱등/권한 | PASS | H2 및 실제 Spring/MySQL API, 익명/타인/위조 요청 거부 |
| PDF/JPEG/PNG 제출·실패 정리 | PASS | 형식/크기/활성 PDF 거부, 중복 해시, 트랜잭션 실패 정리 |
| 비공개 파일 ADMIN 전용 | PASS | 소유자도 본문 403, 미검사 자료 ADMIN 409 |
| 제작 전 변경·삭제 요청, 제작 후 수정·취소 요청 | PASS | 상태 제한, 관리자 승인 후 변경, 계약 스냅샷 불변 |
| 계좌 저장≠승인, 입금 요청≠확인 | PASS | 합성 계좌만 사용, 관리자 승인·수동 확인 및 감사 기록 |
| 4회 전달 진행률·환불 별도 흐름 | PASS | 순서/동시성 제한, 법적 정책 미설정 시 환불 승인 거부 |
| 알림 실패 격리·개인정보 제외 | PASS | SMTP 대역, outbox 재시도; 실제 발송 미검증 |
| 360·390·430·768·1440px | PASS | 실제 로컬 앱, 독립 모바일 컨텍스트, 넘침 검사/캡처 |
| 실제 악성파일 검사기 | 미검증 | 미설정 시 격리; CLEAN 테스트는 명시적 단위 테스트 대역 |
| 운영 DB·브라우저·캐시·실제 입금/환불 | 미검증 | 운영 접근/변경/배포 및 금융 실행 없음 |

로컬 Java: 최초 관련 검사 43/43(신규22 + 기존21), 저장 경로 강화 후 신규23/23 추가 통과. Node 기존6개 테스트 파일 통과. 실제 앱 Playwright 8/8 통과. 공개 static 끝 경로 및 symlink 차단은 마지막 단위 회귀로 검증하며 UI 변경은 없다. CI 결과는 PR의 실제 실행 링크에서 별도로 기록한다.

## 재현

- Java 21: `./gradlew test --tests '*ExamIntakeServiceTest' bootJar`
- 신규 빈 로컬 MySQL에만 `proposed-schema.sql` 설치. 기존 운영 DB에서 실행하지 않는다.
- local profile/합성 데이터, `app.exam-intake.enabled=true`, `app.exam-intake.private-directory=<비공개 임시 경로>`, 포트8091. 이메일/유료 AI 비활성.
- Playwright 의존성은 기존 `e2e/unified-assistant/package-lock.json` 사용. `NODE_PATH=.../node_modules .../playwright test --config e2e/exam-intake/playwright.config.cjs`.
- 반복 테스트는 격리 DB를 새로 만든다. 하루20건 제한을 테스트 편의를 위해 낮추거나 우회하지 않는다.
- CI workflow는 합성 MySQL을 생성·폐기하고 결과/캡처를14일 보존한다. 쿠키·토큰·trace·제출 파일은 업로드하지 않는다.

## 수정 파일 묶음

- `domain/examintake/`: 입력/권한, JDBC 저장, 상태 전이, 비공개 파일·검사, API/화면, 알림, 업로드 크기 필터.
- `exam_service.html`, `exam_service.js`, `exam_service.css`: 문의·신청·관리자 화면.
- `landing.html`, `f2r_home_agent.js`, `admin/hub.html`: 활성화된 경우에만 기존 홈/AI/관리자 허브 연결.
- `ExamIntakeServiceTest`, `e2e/exam-intake/`, `exam-intake-regression.yml`, Build Check 테스트 그룹: 회귀/CI.
- `docs/API.md`, `docs/DOMAIN.md`, `docs/home-exam/*`: 설계/검증/운영 전 승인 항목.

계좌·환불 정책·보존기간·악성파일 검사기·스키마·비공개 저장소가 승인되기 전 기능을 운영에서 켜지 않는다. 자동 실제 파일 삭제/환불 송금은 구현하지 않았다. AI AGENT 드라마 제작과 승인 미디어 파일은 변경하지 않았다.
